package com.htmlcraft.api.render.commands;

import com.htmlcraft.api.render.RenderCommand;
import com.htmlcraft.api.render.RenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 填充矩形指令（Fabric 26.2 版）。
 * <p>对应 {@code GuiGraphicsExtractor.fill(x1, y1, x2, y2, color)}。
 */
public class FillRectCommand implements RenderCommand {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int color;

    public FillRectCommand(int x, int y, int width, int height, int color) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.color = color;
    }

    @Override
    public void execute(GuiGraphicsExtractor graphics, RenderContext context) {
        int ox = context.getOffsetX();
        int oy = context.getOffsetY();
        graphics.fill(ox + x, oy + y, ox + x + width, oy + y + height, color);
    }
}
