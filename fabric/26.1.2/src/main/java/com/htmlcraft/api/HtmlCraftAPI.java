package com.htmlcraft.api;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HtmlCraft API 主入口类（Fabric 26.2 版）。
 * <p>实现 {@link ClientModInitializer}，GUI 渲染为纯客户端能力。
 * <p>原理：Fabric 通过 fabric.mod.json 中 entrypoints.client 指定的类调用 onInitializeClient。
 */
public class HtmlCraftAPI implements ClientModInitializer {
    public static final String MOD_ID = "htmlcraftapi";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("HtmlCraft API 初始化 (Fabric 26.2)");
    }

    /** 构建 Identifier 便捷方法（26.2 使用 Identifier.fromNamespaceAndPath） */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
