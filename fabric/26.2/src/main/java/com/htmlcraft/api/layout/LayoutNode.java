package com.htmlcraft.api.layout;

import com.htmlcraft.api.core.HtmlElement;
import com.htmlcraft.api.core.HtmlText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 布局节点，对应 DOM 树中一个元素或文本经过布局计算后的位置和尺寸。
 * <p>坐标 (x, y) 相对于父容器的内容区域原点。
 */
public class LayoutNode {

    /** 对应的 DOM 元素（元素节点时非空） */
    private final HtmlElement element;

    /** 对应的 DOM 文本节点（文本节点时非空） */
    private final HtmlText textNode;

    private int x, y;
    private int width, height;

    private final List<LayoutNode> children = new ArrayList<>();
    private LayoutNode parent;

    public boolean isTextNode() {
        return textNode != null && element == null;
    }

    public String getTextContent() {
        return textNode != null ? textNode.text() : null;
    }

    public LayoutNode(HtmlElement element) {
        this.element = element;
        this.textNode = null;
    }

    public LayoutNode(HtmlText textNode) {
        this.element = null;
        this.textNode = textNode;
    }

    public HtmlElement getElement() {
        return element;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public LayoutNode getParent() {
        return parent;
    }

    public void addChild(LayoutNode child) {
        if (child == null) return;
        child.parent = this;
        children.add(child);
    }

    public List<LayoutNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public String toString() {
        if (isTextNode()) {
            int len = Math.min(20, textNode.text().length());
            return "LayoutNode{text:\"" + textNode.text().substring(0, len)
                    + "\" @ (" + x + "," + y + ") " + width + "x" + height + "}";
        }
        return "LayoutNode{" + element.getTagName()
                + " @ (" + x + "," + y + ") " + width + "x" + height + "}";
    }
}
