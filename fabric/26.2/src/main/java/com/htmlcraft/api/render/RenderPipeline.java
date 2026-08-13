package com.htmlcraft.api.render;

import com.htmlcraft.api.core.HtmlElement;
import com.htmlcraft.api.layout.LayoutNode;
import com.htmlcraft.api.style.ComputedStyle;
import com.htmlcraft.api.style.ComputedStyle.GradientDir;
import com.htmlcraft.api.style.ComputedStyle.Overflow;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * HTML 渲染管线（即时遍历式，Fabric 26.2 版）。
 *
 * <p>26.2 主要 API 变化：
 * <ul>
 *   <li>{@code DrawContext} → {@link GuiGraphicsExtractor}</li>
 *   <li>{@code graphics.drawText(...)} → {@code graphics.text(...)}</li>
 *   <li>{@code fillGradient / fill / fillGradientH} 去掉 z 参数</li>
 *   <li>{@code ColorHelper.Argb} → {@link ARGB}（静态方法：alpha/red/green/blue/color）</li>
 *   <li>Matrix3x2fStack：push/pop/translate/scale 仍为同名方法</li>
 * </ul>
 */
public class RenderPipeline {

    private final java.util.Map<String, Integer> scrollOffsets = new java.util.concurrent.ConcurrentHashMap<>();

    private final RenderContext context;

    public RenderPipeline(RenderContext context) {
        this.context = context;
    }

    public int getScroll(String elementId) {
        return scrollOffsets.getOrDefault(elementId, 0);
    }
    public void setScroll(String elementId, int scrollY) {
        scrollOffsets.put(elementId, Math.max(0, scrollY));
    }
    public void addScroll(String elementId, int delta) {
        int cur = scrollOffsets.getOrDefault(elementId, 0);
        scrollOffsets.put(elementId, Math.max(0, cur + delta));
    }

    public void build(LayoutNode layoutNode) {
    }

    public void render(GuiGraphicsExtractor graphics, LayoutNode layoutNode) {
        if (layoutNode == null) return;
        renderNode(graphics, layoutNode, context.getOffsetX(), context.getOffsetY(),
                   null, null);
    }

    public void render(GuiGraphicsExtractor graphics) {
    }

    public void clear() {
        scrollOffsets.clear();
    }

    // ===== 核心渲染递归 =====

    private void renderNode(GuiGraphicsExtractor graphics, LayoutNode node,
                            int absX, int absY,
                            int[] scissor, String scrollKey) {
        if (node == null) return;
        HtmlElement element = node.getElement();
        if (element == null) return;

        ComputedStyle style = (ComputedStyle) element.getComputedStyle();
        if (style == null) style = ComputedStyle.DEFAULT;
        if (style.getDisplay() == ComputedStyle.DisplayType.NONE) return;

        int x = absX + node.getX();
        int y = absY + node.getY();

        int scrolledY = y;
        if (scrollKey != null) {
            scrolledY += getScroll(scrollKey);
        }

        int w = node.getWidth();
        int h = node.getHeight();

        int[] padding = style.getPadding();
        String elemId = element.getId();

        // ========== 1. 计算新的 scissor 边界（x1,y1,x2,y2） ==========
        int[] newScissor = scissor;
        Overflow ox = style.getOverflowX(), oy = style.getOverflowY();
        boolean overflowClip = (ox == Overflow.HIDDEN || ox == Overflow.SCROLL || ox == Overflow.AUTO
                                || oy == Overflow.HIDDEN || oy == Overflow.SCROLL || oy == Overflow.AUTO);
        if (overflowClip) {
            int sx1 = x, sy1 = y, sx2 = x + w, sy2 = y + h;
            if (scissor != null) {
                sx1 = Math.max(sx1, scissor[0]);
                sy1 = Math.max(sy1, scissor[1]);
                sx2 = Math.min(sx2, scissor[2]);
                sy2 = Math.min(sy2, scissor[3]);
            }
            if (sx2 > sx1 && sy2 > sy1) {
                newScissor = new int[] {sx1, sy1, sx2, sy2};
            } else {
                return;
            }
        }

        // ========== 2. 开启 scissor ==========
        // 26.2: GuiGraphicsExtractor.enableScissor(x0,y0,x1,y1) — 左上右下坐标
        boolean pushedScissor = false;
        if (newScissor != null) {
            graphics.enableScissor(newScissor[0], newScissor[1],
                                   newScissor[2], newScissor[3]);
            pushedScissor = true;
        }

        try {
            int borderRadius = Math.min(style.getBorderRadius(),
                                        Math.min(w, h) / 2);

            // 3. box-shadow
            ComputedStyle.BoxShadow sh = style.getBoxShadow();
            if (sh != null && (sh.color >>> 24) != 0) {
                drawBoxShadow(graphics, x, scrolledY, w, h, borderRadius, sh);
            }

            // 4. 背景：渐变优先，其次纯色
            ComputedStyle.LinearGradient grad = style.getLinearGradient();
            int bgColor = style.getBackgroundColor();
            if (grad != null && grad.dir != GradientDir.NONE) {
                drawGradientBg(graphics, x, scrolledY, w, h, borderRadius, grad);
            } else if ((bgColor >>> 24) != 0) {
                if (borderRadius > 0) {
                    drawRoundedFill(graphics, x, scrolledY, w, h, borderRadius, bgColor);
                } else {
                    graphics.fill(x, scrolledY, x + w, scrolledY + h, bgColor);
                }
            }

            // 5. 边框
            int borderWidth = style.getBorderWidth();
            if (borderWidth > 0) {
                drawRoundedBorder(graphics, x, scrolledY, w, h, borderRadius, borderWidth, style.getBorderColor());
            }

            // 7. 子元素（按 zIndex 排序，包含文本节点）
            List<LayoutNode> children = node.getChildren();
            if (children != null && !children.isEmpty()) {
                List<LayoutNode> sorted = new ArrayList<>(children);
                sorted.sort(Comparator.comparingInt(cn -> {
                    if (cn.isTextNode()) return 0;
                    HtmlElement ce = cn.getElement();
                    if (ce == null) return 0;
                    ComputedStyle cs = (ComputedStyle) ce.getComputedStyle();
                    return cs == null ? 0 : cs.getZIndex();
                }));

                boolean isScrollContainer =
                    (oy == Overflow.SCROLL || oy == Overflow.AUTO) && elemId != null && !elemId.isEmpty();
                String childScrollKey = isScrollContainer ? elemId : scrollKey;

                for (LayoutNode child : sorted) {
                    if (child.isTextNode()) {
                        renderTextNode(graphics, child, style, x, scrolledY);
                    } else {
                        renderNode(graphics, child, x, scrolledY,
                                   newScissor, childScrollKey);
                    }
                }

                // 8. 滚动条
                if (isScrollContainer) {
                    int contentTotalH = estimateContentHeight(node);
                    int viewH = h - padding[0] - padding[2];
                    if (contentTotalH > viewH) {
                        drawScrollBar(graphics, x, y, w, h, viewH, contentTotalH,
                                      getScroll(elemId), padding);
                    }
                }
            }

        } finally {
            if (pushedScissor) {
                graphics.disableScissor();
            }
        }
    }

    /**
     * 渲染文本节点。
     * <p>26.2：{@code drawText} → {@code graphics.text(font, text, x, y, color, shadow)}。
     */
    private void renderTextNode(GuiGraphicsExtractor graphics, LayoutNode textNode,
                                 ComputedStyle parentStyle, int parentX, int parentY) {
        String text = textNode.getTextContent();
        if (text == null || text.isEmpty()) return;

        int absX = parentX + textNode.getX();
        int absY = parentY + textNode.getY();

        int fontPx = Math.max(8, parentStyle.getFontSize());
        float fontScale = (float) fontPx / 12.0f;
        int color = applyOpacityToColor(parentStyle.getColor(), parentStyle.getOpacity());

        if (fontScale != 1.0f && fontScale > 0) {
            float sx = (float) absX / fontScale;
            float sy = (float) absY / fontScale;
            // 26.2: pose() 返回 Matrix3x2fStack，方法名 pushMatrix/popMatrix
            graphics.pose().pushMatrix();
            graphics.pose().scale(fontScale, fontScale);
            graphics.text(context.getFont(), text, Math.round(sx), Math.round(sy), color, false);
            graphics.pose().popMatrix();
        } else {
            graphics.text(context.getFont(), text, absX, absY, color, false);
        }
    }

    private static int applyOpacityToColor(int color, float opacity) {
        if (opacity >= 0.999f) return color;
        int a = (int) (((color >>> 24) & 0xFF) * opacity);
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (a << 24) | (color & 0xFFFFFF);
    }

    // ===== 渐变背景 =====
    private static void drawGradientBg(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                       int radius, ComputedStyle.LinearGradient grad) {
        if (w <= 0 || h <= 0) return;
        int c1 = grad.colorStart;
        int c2 = grad.colorEnd;

        if (radius > 0) {
            drawRoundedGradient(g, x, y, w, h, radius, grad);
            return;
        }

        switch (grad.dir) {
            case TO_BOTTOM:
                g.fillGradient(x, y, x + w, y + h, c1, c2);
                break;
            case TO_TOP:
                g.fillGradient(x, y, x + w, y + h, c2, c1);
                break;
            case TO_RIGHT:
                fillGradientH(g, x, y, x + w, y + h, c1, c2);
                break;
            case TO_LEFT:
                fillGradientH(g, x, y, x + w, y + h, c2, c1);
                break;
            default:
                g.fill(x, y, x + w, y + h, c1);
        }
    }

    /** 水平渐变（26.2：改用 net.minecraft.util.ARGB 通道获取方法） */
    private static void fillGradientH(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2,
                                      int colL, int colR) {
        if (x2 <= x1) return;
        int w = x2 - x1;
        // 26.2：ColorHelper 被移除，改用 ARGB 工具类（静态方法名与原 ColorHelper.Argb.getXxx 一致）
        float a1 = ARGB.alpha(colL) / 255f;
        float r1 = ARGB.red(colL)   / 255f;
        float g1 = ARGB.green(colL) / 255f;
        float b1 = ARGB.blue(colL)  / 255f;
        float a2 = ARGB.alpha(colR) / 255f;
        float r2 = ARGB.red(colR)   / 255f;
        float g2 = ARGB.green(colR) / 255f;
        float b2 = ARGB.blue(colR)  / 255f;
        int step = Math.max(1, Math.min(w / 64, 4));
        for (int i = 0; i < w; i += step) {
            float t = (float) i / (w - 1);
            int a = (int) ((a1 + (a2 - a1) * t) * 255);
            int r = (int) ((r1 + (r2 - r1) * t) * 255);
            int gg = (int) ((g1 + (g2 - g1) * t) * 255);
            int b = (int) ((b1 + (b2 - b1) * t) * 255);
            int col = (a << 24) | (r << 16) | (gg << 8) | b;
            int cx1 = x1 + i;
            int cx2 = Math.min(x2, cx1 + step);
            g.fill(cx1, y1, cx2, y2, col);
        }
    }

    // ===== BoxShadow =====
    private static void drawBoxShadow(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                      int radius, ComputedStyle.BoxShadow sh) {
        if ((sh.color >>> 24) == 0) return;
        int blur = Math.max(0, sh.blur);
        int spread = sh.spread;
        int ox = sh.offsetX, oy = sh.offsetY;
        int bx = x + ox - spread - blur;
        int by = y + oy - spread - blur;
        int bw = w + spread * 2 + blur * 2;
        int bh = h + spread * 2 + blur * 2;
        if (bw <= 0 || bh <= 0) return;

        int layers = Math.max(1, Math.min(blur, 8));
        int baseA = (sh.color >>> 24) & 0xFF;
        int rgb = sh.color & 0xFFFFFF;
        for (int i = 0; i < layers; i++) {
            float t = (float) i / layers;
            int a = (int) (baseA * (1.0f - t * 0.7f) / layers);
            if (a < 0) a = 0;
            if (a > 255) a = 255;
            int layerColor = (a << 24) | rgb;
            int inset = (blur - i);
            int lx = bx + inset;
            int ly = by + inset;
            int lw = bw - inset * 2;
            int lh = bh - inset * 2;
            if (lw <= 0 || lh <= 0) continue;
            int shadowR = Math.min(radius + blur / 2, Math.min(lw, lh) / 2);
            if (shadowR > 0) {
                drawRoundedFill(g, lx, ly, lw, lh, shadowR, layerColor);
            } else {
                g.fill(lx, ly, lx + lw, ly + lh, layerColor);
            }
        }
    }

    // ===== 圆角矩形填充 =====
    private static void drawRoundedFill(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        if ((color >>> 24) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int x2 = x + w;
        int y2 = y + h;
        g.fill(x + r, y, x2 - r, y + r, color);
        g.fill(x + r, y2 - r, x2 - r, y2, color);
        g.fill(x, y + r, x2, y2 - r, color);
        drawCorner(g, x + r, y + r,           r, color, false, false);
        drawCorner(g, x2 - r, y + r,          r, color, true,  false);
        drawCorner(g, x + r, y2 - r,          r, color, false, true);
        drawCorner(g, x2 - r, y2 - r,         r, color, true,  true);
    }

    private static void drawCorner(GuiGraphicsExtractor g, int cx, int cy, int r, int color,
                                   boolean rx, boolean ry) {
        int rr = r * r;
        for (int dy = 0; dy < r; dy++) {
            int dy2 = (ry ? dy : (r - 1 - dy));
            int yLine = cy + (ry ? dy : -dy);
            int dxMax = (int) Math.sqrt(rr - dy2 * dy2);
            if (dxMax < 0) continue;
            if (rx) {
                g.fill(cx, yLine, cx + dxMax, yLine + 1, color);
            } else {
                g.fill(cx - dxMax, yLine, cx, yLine + 1, color);
            }
        }
    }

    /** 圆角渐变（简单近似） */
    private static void drawRoundedGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int r,
                                            ComputedStyle.LinearGradient grad) {
        if (grad.dir == GradientDir.TO_BOTTOM) {
            g.fillGradient(x, y, x + w, y + h, grad.colorStart, grad.colorEnd);
        } else if (grad.dir == GradientDir.TO_TOP) {
            g.fillGradient(x, y, x + w, y + h, grad.colorEnd, grad.colorStart);
        } else if (grad.dir == GradientDir.TO_RIGHT) {
            fillGradientH(g, x, y, x + w, y + h, grad.colorStart, grad.colorEnd);
        } else if (grad.dir == GradientDir.TO_LEFT) {
            fillGradientH(g, x, y, x + w, y + h, grad.colorEnd, grad.colorStart);
        }
    }

    /** 圆角边框 */
    private static void drawRoundedBorder(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                          int r, int bw, int color) {
        if ((color >>> 24) == 0 || bw <= 0) return;
        int x2 = x + w;
        int y2 = y + h;
        r = Math.min(r, Math.min(w, h) / 2);

        if (r <= 0) {
            g.fill(x, y, x2, y + bw, color);
            g.fill(x, y2 - bw, x2, y2, color);
            g.fill(x, y, x + bw, y2, color);
            g.fill(x2 - bw, y, x2, y2, color);
            return;
        }
        drawRoundedFill(g, x, y, w, h, r, color);
        int ix1 = x + bw, ix2 = x2 - bw, iy1 = y + bw, iy2 = y2 - bw;
        if (bw <= r) {
            g.fill(x + r, y, x2 - r, y + bw, color);
            g.fill(x + r, y2 - bw, x2 - r, y2, color);
            g.fill(x, y + r, x + bw, y2 - r, color);
            g.fill(x2 - bw, y + r, x2, y2 - r, color);
            drawRingCorner(g, x + r, y + r, r, bw, color, false, false);
            drawRingCorner(g, x2 - r, y + r, r, bw, color, true, false);
            drawRingCorner(g, x + r, y2 - r, r, bw, color, false, true);
            drawRingCorner(g, x2 - r, y2 - r, r, bw, color, true, true);
        }
    }

    private static void drawRingCorner(GuiGraphicsExtractor g, int cx, int cy, int r, int bw, int color,
                                       boolean rx, boolean ry) {
        int rOut2 = r * r;
        int rIn2 = (r - bw) * (r - bw);
        for (int dy = 0; dy < r; dy++) {
            int dy2 = (ry ? dy : (r - 1 - dy));
            int yLine = cy + (ry ? dy : -dy);
            int d2 = dy2 * dy2;
            int dxOut = (int) Math.sqrt(rOut2 - d2);
            int dxIn = (int) Math.ceil(Math.sqrt(Math.max(0, rIn2 - d2)));
            if (rx) {
                if (dxOut > dxIn) {
                    g.fill(cx + dxIn, yLine, cx + dxOut, yLine + 1, color);
                }
            } else {
                if (dxOut > dxIn) {
                    g.fill(cx - dxOut, yLine, cx - dxIn, yLine + 1, color);
                }
            }
        }
    }

    private static int estimateContentHeight(LayoutNode node) {
        if (node == null || node.getChildren() == null) return 0;
        int maxBottom = 0;
        for (LayoutNode cn : node.getChildren()) {
            maxBottom = Math.max(maxBottom, cn.getY() + cn.getHeight());
        }
        return maxBottom;
    }

    private static void drawScrollBar(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                      int viewH, int contentH, int scrollY, int[] padding) {
        int barW = 6;
        int bx = x + w - barW - 2;
        int by = y + padding[0];
        int trackH = h - padding[0] - padding[2];
        if (trackH <= 0) return;
        g.fill(bx, by, bx + barW, by + trackH, 0x40FFFFFF);
        float ratio = (float) viewH / contentH;
        int thumbH = Math.max(20, (int) (trackH * ratio));
        int maxScroll = Math.max(0, contentH - viewH);
        int scrollRatio = maxScroll == 0 ? 0 : Math.min(scrollY, maxScroll) * (trackH - thumbH) / maxScroll;
        int ty = by + scrollRatio;
        g.fill(bx, ty, bx + barW, ty + thumbH, 0xCC808080);
    }
}
