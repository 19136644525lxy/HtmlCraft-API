package com.htmlcraft.api.render.commands;

import com.htmlcraft.api.render.RenderCommand;
import com.htmlcraft.api.render.RenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 绘制文本指令（Fabric 26.2 版）。
 * <p>26.2 变化：{@code DrawContext.drawText} → {@code GuiGraphicsExtractor.text(font, text, x, y, color, shadow)}。
 */
public class DrawTextCommand implements RenderCommand {

    private final int x;
    private final int y;
    private final String text;
    private final int color;
    private final boolean shadow;

    public DrawTextCommand(int x, int y, String text, int color, boolean shadow) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
        this.shadow = shadow;
    }

    @Override
    public void execute(GuiGraphicsExtractor graphics, RenderContext context) {
        int ox = context.getOffsetX();
        int oy = context.getOffsetY();
        graphics.text(context.getFont(), text, ox + x, oy + y, color, shadow);
    }
}
