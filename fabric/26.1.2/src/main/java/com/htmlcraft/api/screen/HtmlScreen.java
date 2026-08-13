package com.htmlcraft.api.screen;

import com.htmlcraft.api.core.HtmlDocument;
import com.htmlcraft.api.core.HtmlElement;
import com.htmlcraft.api.layout.LayoutEngine;
import com.htmlcraft.api.layout.LayoutNode;
import com.htmlcraft.api.parser.HtmlParser;
import com.htmlcraft.api.render.RenderContext;
import com.htmlcraft.api.render.RenderPipeline;
import com.htmlcraft.api.style.ComputedStyle;
import com.htmlcraft.api.style.StyleCalculator;
import com.htmlcraft.api.style.StyleSheet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * HTML 渲染屏幕抽象基类（Fabric 26.2 版）。
 *
 * <p>26.2 关键变化：
 * <ul>
 *   <li>Screen 包路径：{@code gui.screen} → {@code gui.screens}（复数）</li>
 *   <li>Text → Component（{@code net.minecraft.network.chat.Component}）</li>
 *   <li>{@code render(DrawContext,...)} → {@code extractRenderState(GuiGraphicsExtractor,...)}</li>
 *   <li>mouseScrolled：3 参数 → 4 参数（x, y, scrollX, scrollY），amount 取 scrollY</li>
 *   <li>mouseClicked：(x,y,button) → (MouseButtonEvent, doubleClick)，通过 event.getXxx() 取值</li>
 *   <li>shouldPause()：方法移除，不再需要</li>
 *   <li>matrix pose：push/pop/translate/scale 保留原方法名</li>
 * </ul>
 */
public abstract class HtmlScreen extends Screen {

    private RenderPipeline pipeline;
    private LayoutNode rootLayout;
    private final List<HitTarget> hitTargets = new CopyOnWriteArrayList<>();
    private volatile Consumer<ClickEvent> clickHandler;

    private int preferredWidth;
    private int preferredHeight;

    private int layoutOffsetX;
    private int layoutOffsetY;

    protected HtmlScreen(Component title) {
        super(title);
    }

    protected void setPreferredSize(int width, int height) {
        this.preferredWidth = Math.max(0, width);
        this.preferredHeight = Math.max(0, height);
    }

    public int getLayoutOffsetX() {
        return layoutOffsetX;
    }

    public int getLayoutOffsetY() {
        return layoutOffsetY;
    }

    protected abstract String getHtml();

    protected String getCss() {
        return null;
    }

    public void setClickHandler(Consumer<ClickEvent> handler) {
        this.clickHandler = handler;
    }

    @Override
    protected void init() {
        rebuild();
    }

    public void rebuild() {
        HtmlDocument doc = HtmlParser.parse(getHtml());
        HtmlElement root = doc.getRootElement();
        if (root == null) {
            pipeline = null;
            rootLayout = null;
            hitTargets.clear();
            return;
        }

        StyleSheet styleSheet = new StyleSheet();
        String css = getCss();
        if (css != null && !css.isEmpty()) {
            styleSheet.loadCss(css);
        }
        StyleCalculator.computeStyles(root, styleSheet);

        int contentW = Math.max(1, preferredWidth > 0 ? preferredWidth : this.width);
        int contentH = Math.max(1, preferredHeight > 0 ? preferredHeight : this.height);
        if (preferredHeight <= 0) {
            contentH = Math.max(1, this.height);
        }
        layoutOffsetX = Math.max(0, (this.width - contentW) / 2);
        layoutOffsetY = Math.max(0, (this.height - contentH) / 2);

        rootLayout = LayoutEngine.layout(root, contentW, contentH);

        RenderContext context = new RenderContext();
        pipeline = new RenderPipeline(context);
        pipeline.clear();

        hitTargets.clear();
        collectHitTargets(rootLayout, layoutOffsetX, layoutOffsetY);
    }

    public LayoutNode getRootLayout() {
        return rootLayout;
    }

    public RenderPipeline getPipeline() {
        return pipeline;
    }

    private String findScrollContainerAt(LayoutNode node, int absX, int absY,
                                          int parentX, int parentY) {
        if (node == null) return null;
        HtmlElement el = node.getElement();
        if (el == null) return null;
        ComputedStyle cs = el.getComputedStyle() instanceof ComputedStyle s
                ? s : ComputedStyle.DEFAULT;
        if (cs.getDisplay() == ComputedStyle.DisplayType.NONE) return null;

        int x = parentX + node.getX();
        int y = parentY + node.getY();
        int w = node.getWidth();
        int h = node.getHeight();

        String childResult = null;
        for (LayoutNode ch : node.getChildren()) {
            String r = findScrollContainerAt(ch, absX, absY, x, y);
            if (r != null) { childResult = r; break; }
        }
        if (childResult != null) return childResult;

        String id = el.getId();
        if (id != null && !id.isEmpty()
                && (cs.getOverflowY() == ComputedStyle.Overflow.SCROLL
                    || cs.getOverflowY() == ComputedStyle.Overflow.AUTO)
                && absX >= x && absX <= x + w && absY >= y && absY <= y + h) {
            return id;
        }
        return null;
    }

    private void collectHitTargets(LayoutNode node, int parentX, int parentY) {
        if (node == null || node.getElement() == null) return;

        HtmlElement el = node.getElement();
        ComputedStyle style = el.getComputedStyle() instanceof ComputedStyle cs
                ? cs : ComputedStyle.DEFAULT;
        if (style.getDisplay() == ComputedStyle.DisplayType.NONE) return;

        int absX = parentX + node.getX();
        int absY = parentY + node.getY();

        String tag = el.tagName();
        if ("button".equals(tag) || "a".equals(tag) || el.getId() != null) {
            hitTargets.add(new HitTarget(el, absX, absY,
                    Math.max(0, node.getWidth()), Math.max(0, node.getHeight())));
        }

        for (LayoutNode child : node.getChildren()) {
            collectHitTargets(child, absX, absY);
        }
    }

    /**
     * 26.2：render → extractRenderState，Screen 父类不再主动渲染到 GPU，
     * 而是"提取渲染状态"交给新的渲染管线。渲染调用名基本等价。
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        if (pipeline != null && rootLayout != null) {
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) layoutOffsetX, (float) layoutOffsetY);
            pipeline.render(graphics, rootLayout);
            graphics.pose().popMatrix();
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    /**
     * 26.2：mouseScrolled 改为 4 参数（x, y, scrollX, scrollY），垂直滚动值取 scrollY。
     */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (pipeline != null && rootLayout != null) {
            int px = (int) x - layoutOffsetX;
            int py = (int) y - layoutOffsetY;
            String scrollId = findScrollContainerAt(rootLayout, px, py, 0, 0);
            if (scrollId != null) {
                pipeline.addScroll(scrollId, (int) (-scrollY * 20));
                return true;
            }
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    /**
     * 26.2：mouseClicked 改为 (MouseButtonEvent event, boolean doubleClick)，
     * 通过 event 提供的 getter 获取鼠标位置(x,y)与按钮号(button)。
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int px = (int) event.x();
        int py = (int) event.y();
        int button = event.button();
        for (int i = hitTargets.size() - 1; i >= 0; i--) {
            HitTarget target = hitTargets.get(i);
            if (target.contains(px, py)) {
                Consumer<ClickEvent> handler = clickHandler;
                if (handler != null) {
                    handler.accept(new ClickEvent(target.element(), button, px, py));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    public static class ClickEvent {
        private final HtmlElement element;
        private final int button;
        private final int x;
        private final int y;

        public ClickEvent(HtmlElement element, int button, int x, int y) {
            this.element = element;
            this.button = button;
            this.x = x;
            this.y = y;
        }

        public HtmlElement element() {
            return element;
        }

        public int button() {
            return button;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }
    }

    private record HitTarget(HtmlElement element, int x, int y,
                              int width, int height) {
        boolean contains(int px, int py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }
}
