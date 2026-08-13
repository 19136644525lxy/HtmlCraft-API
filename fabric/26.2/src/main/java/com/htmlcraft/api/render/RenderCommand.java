package com.htmlcraft.api.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 渲染指令接口（Fabric 26.2 版）。
 * <p>所有具体渲染指令（填充矩形、绘制文本、绘制纹理等）均实现此接口，
 * 由 {@link RenderPipeline} 统一收集并顺序执行。
 *
 * <p>26.2 变化：参数类型 {@code DrawContext} → {@link GuiGraphicsExtractor}。
 */
public interface RenderCommand {

    /**
     * 执行单条渲染指令。
     *
     * @param graphics Minecraft 绘图上下文（26.2 中为 GuiGraphicsExtractor，提供 fill / text / blit 等）
     * @param context  渲染上下文，持有字体、全局偏移量等状态
     */
    void execute(GuiGraphicsExtractor graphics, RenderContext context);
}
