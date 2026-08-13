package com.htmlcraft.api.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * 渲染上下文（Fabric 26.2 版）。
 * <p>26.2 关键变化：
 * <ul>
 *   <li>MinecraftClient → Minecraft（类名去掉 Client 后缀）</li>
 *   <li>TextRenderer → Font（类和包路径变：net.minecraft.client.gui.Font）</li>
 *   <li>client.textRenderer → client.font（Minecraft 类字段改名为 public final Font font）</li>
 * </ul>
 */
public class RenderContext {

    private final Minecraft client;
    private final Font font;

    private int offsetX = 0;
    private int offsetY = 0;

    public RenderContext() {
        this.client = Minecraft.getInstance();
        // 26.2：Minecraft 提供 public final Font font 字段（替代原 textRenderer）
        this.font = client.font;
    }

    public Minecraft getMinecraft() {
        return client;
    }

    public Font getFont() {
        return font;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public void setOffset(int x, int y) {
        this.offsetX = x;
        this.offsetY = y;
    }
}
