package com.htmlcraft.api.render.commands;

import com.htmlcraft.api.render.RenderCommand;
import com.htmlcraft.api.render.RenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 绘制边框指令（Fabric 26.2 版）。
 * <p>通过 4 次 {@code GuiGraphicsExtractor.fill} 绘制四条边。
 */
public class DrawBorderCommand implements RenderCommand {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int borderWidth;
    private final int color;

    public DrawBorderCommand(int x, int y, int width, int height, int borderWidth, int color) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.borderWidth = borderWidth;
        this.color = color;
    }

    @Override
    public void execute(GuiGraphicsExtractor graphics, RenderContext context) {
        int ox = context.getOffsetX();
        int oy = context.getOffsetY();
        int x1 = ox + x;
        int y1 = oy + y;
        int x2 = ox + x + width;
        int y2 = oy + y + height;
        int bw = borderWidth;

        graphics.fill(x1, y1, x2, y1 + bw, color);
        graphics.fill(x1, y2 - bw, x2, y2, color);
        graphics.fill(x1, y1, x1 + bw, y2, color);
        graphics.fill(x2 - bw, y1, x2, y2, color);
    }
}
