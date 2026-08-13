package com.htmlcraft.api.style;

import com.htmlcraft.api.core.HtmlElement;
import com.htmlcraft.api.parser.CssParser;
import com.htmlcraft.api.parser.CssParser.CssRule;
import com.htmlcraft.api.parser.CssParser.Selector;
import com.htmlcraft.api.parser.CssParser.SimpleSelector;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 样式表，包含 CSS 规则列表并提供元素匹配能力。
 * 线程安全：规则列表使用 CopyOnWriteArrayList。
 */
public class StyleSheet {

    private final CopyOnWriteArrayList<CssRule> rules = new CopyOnWriteArrayList<>();

    public void addRule(CssRule rule) {
        if (rule != null) rules.add(rule);
    }

    public void addRules(List<CssRule> newRules) {
        if (newRules != null) {
            for (CssRule rule : newRules) {
                if (rule != null) rules.add(rule);
            }
        }
    }

    public void loadCss(String css) {
        addRules(CssParser.parse(css));
    }

    public List<CssRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public void clear() {
        rules.clear();
    }

    public Map<String, String> match(HtmlElement element) {
        Map<String, String> result = new HashMap<>();
        for (CssRule rule : rules) {
            if (matches(rule.getSelector(), element)) {
                Map<String, String> props = rule.getProperties();
                if (props != null) result.putAll(props);
            }
        }
        return result;
    }

    private boolean matches(Selector selector, HtmlElement element) {
        if (selector == null || element == null) return false;
        List<SimpleSelector> parts = selector.getParts();
        if (parts.isEmpty()) return false;

        int idx = parts.size() - 1;
        if (!matchesSimple(parts.get(idx), element)) return false;
        idx--;

        HtmlElement ancestor = element.getParent();
        while (idx >= 0 && ancestor != null) {
            if (matchesSimple(parts.get(idx), ancestor)) {
                idx--;
            }
            ancestor = ancestor.getParent();
        }
        return idx < 0;
    }

    private boolean matchesSimple(SimpleSelector ss, HtmlElement element) {
        if (ss.isUniversal()) return true;
        if (ss.getTag() != null && !ss.getTag().equals(element.getTagName())) return false;
        if (ss.getId() != null && !ss.getId().equals(element.getId())) return false;
        if (ss.getClasses() != null) {
            for (String cls : ss.getClasses()) {
                if (!element.hasClass(cls)) return false;
            }
        }
        return true;
    }
}
