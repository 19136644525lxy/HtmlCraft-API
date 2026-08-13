package com.htmlcraft.api.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CSS 解析器，将 CSS 文本解析为 {@link CssRule} 列表。
 */
public class CssParser {

    public static List<CssRule> parse(String css) {
        List<CssRule> rules = new ArrayList<>();
        if (css == null || css.isEmpty()) return rules;

        int length = css.length();
        int pos = 0;

        while (pos < length) {
            pos = skipWhitespaceAndComments(css, pos, length);
            if (pos >= length) break;

            int bracePos = css.indexOf('{', pos);
            if (bracePos < 0) break;

            String selectorStr = css.substring(pos, bracePos).trim();
            pos = bracePos + 1;

            int closePos = css.indexOf('}', pos);
            if (closePos < 0) closePos = length;

            String propsStr = css.substring(pos, closePos);
            pos = closePos + 1;

            if (selectorStr.isEmpty()) continue;

            List<Selector> selectors = parseSelectors(selectorStr);
            Map<String, String> properties = parseProperties(propsStr);

            for (Selector selector : selectors) {
                rules.add(new CssRule(selector, properties));
            }
        }
        return rules;
    }

    private static List<Selector> parseSelectors(String selectorStr) {
        List<Selector> selectors = new ArrayList<>();
        for (String part : selectorStr.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            Selector sel = parseSelector(part);
            if (sel != null) selectors.add(sel);
        }
        return selectors;
    }

    private static Selector parseSelector(String selectorStr) {
        List<SimpleSelector> parts = new ArrayList<>();
        String[] tokens = selectorStr.split("\\s+");
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            SimpleSelector ss = parseSimpleSelector(token);
            if (ss != null) parts.add(ss);
        }
        if (parts.isEmpty()) return null;
        return new Selector(parts);
    }

    private static SimpleSelector parseSimpleSelector(String token) {
        String tag = null;
        String id = null;
        List<String> classes = new ArrayList<>();
        boolean universal = false;

        int i = 0;
        int len = token.length();

        if (i < len) {
            char c = token.charAt(i);
            if (c == '*') {
                universal = true;
                i++;
            } else if (Character.isLetter(c)) {
                int start = i;
                while (i < len) {
                    char ch = token.charAt(i);
                    if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                tag = token.substring(start, i).toLowerCase(Locale.ROOT);
            }
        }

        while (i < len) {
            char c = token.charAt(i);
            if (c == '.') {
                i++;
                int start = i;
                while (i < len) {
                    char ch = token.charAt(i);
                    if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                if (i > start) classes.add(token.substring(start, i));
            } else if (c == '#') {
                i++;
                int start = i;
                while (i < len) {
                    char ch = token.charAt(i);
                    if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                if (i > start) id = token.substring(start, i);
            } else {
                i++;
            }
        }

        return new SimpleSelector(tag, id, classes, universal);
    }

    private static Map<String, String> parseProperties(String propsStr) {
        Map<String, String> properties = new LinkedHashMap<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < propsStr.length(); i++) {
            char c = propsStr.charAt(i);

            if (c == '(') {
                depth++;
                current.append(c);
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
                current.append(c);
            } else if (c == ';' && depth == 0) {
                addProperty(properties, current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            addProperty(properties, current.toString());
        }

        return properties;
    }

    private static void addProperty(Map<String, String> properties, String decl) {
        decl = decl.trim();
        if (decl.isEmpty()) return;
        int colon = decl.indexOf(':');
        if (colon < 0) return;
        String name = decl.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String value = decl.substring(colon + 1).trim();
        if (!name.isEmpty() && !value.isEmpty()) {
            properties.put(name, value);
        }
    }

    private static int skipWhitespaceAndComments(String css, int pos, int length) {
        while (pos < length) {
            while (pos < length && Character.isWhitespace(css.charAt(pos))) {
                pos++;
            }
            if (pos + 1 < length && css.charAt(pos) == '/' && css.charAt(pos + 1) == '*') {
                pos += 2;
                while (pos + 1 < length && !(css.charAt(pos) == '*' && css.charAt(pos + 1) == '/')) {
                    pos++;
                }
                pos += 2;
            } else {
                break;
            }
        }
        return pos;
    }

    public static class CssRule {
        private final Selector selector;
        private final Map<String, String> properties;

        public CssRule(Selector selector, Map<String, String> properties) {
            this.selector = selector;
            this.properties = properties;
        }

        public Selector getSelector() {
            return selector;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public String toString() {
            return "CssRule{" + selector + " -> " + properties + "}";
        }
    }

    public static class Selector {
        private final List<SimpleSelector> parts;

        public Selector(List<SimpleSelector> parts) {
            this.parts = parts;
        }

        public List<SimpleSelector> getParts() {
            return parts;
        }

        @Override
        public String toString() {
            return String.join(" ", parts.stream().map(Object::toString).toList());
        }
    }

    public static class SimpleSelector {
        private final String tag;
        private final String id;
        private final List<String> classes;
        private final boolean universal;

        public SimpleSelector(String tag, String id, List<String> classes, boolean universal) {
            this.tag = tag;
            this.id = id;
            this.classes = classes;
            this.universal = universal;
        }

        public String getTag() {
            return tag;
        }

        public String getId() {
            return id;
        }

        public List<String> getClasses() {
            return classes;
        }

        public boolean isUniversal() {
            return universal;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (universal) {
                sb.append('*');
            } else if (tag != null) {
                sb.append(tag);
            }
            if (id != null) sb.append('#').append(id);
            for (String c : classes) sb.append('.').append(c);
            return sb.toString();
        }
    }
}
