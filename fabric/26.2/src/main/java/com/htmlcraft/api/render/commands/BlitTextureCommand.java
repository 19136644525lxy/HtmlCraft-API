package com.htmlcraft.api.render.commands;

import com.htmlcraft.api.render.RenderCommand;
import com.htmlcraft.api.render.RenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * 绘制纹理指令（Fabric 26.2 版）。
 * <p>26.2 变化：GuiGraphicsExtractor.blit 签名变更为
 *    {@code blit(Identifier, x0, y0, x1, y1, u0, u1, v0, v1)}（左上+右下坐标，UV归一化范围）。
 */
public class BlitTextureCommand implements RenderCommand {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Identifier location;

    public BlitTextureCommand(int x, int y, int width, int height, Identifier location) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.location = location;
    }

    @Override
    public void execute(GuiGraphicsExtractor graphics, RenderContext context) {
        int ox = context.getOffsetX();
        int oy = context.getOffsetY();
        int x0 = ox + x;
        int y0 = oy + y;
        int x1 = x0 + width;
        int y1 = y0 + height;
        // 26.2: blit(id, 左上x,左上y, 右下x,右下y, u起点,u终点, v起点,v终点)
        // 对于整张贴图，UV 范围 0.0~1.0
        graphics.blit(location, x0, y0, x1, y1, 0.0F, 1.0F, 0.0F, 1.0F);
    }
}
