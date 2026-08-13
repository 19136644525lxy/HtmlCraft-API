package com.htmlcraft.api.parser;

import com.htmlcraft.api.core.HtmlDocument;
import com.htmlcraft.api.core.HtmlElement;
import com.htmlcraft.api.core.HtmlNode;
import com.htmlcraft.api.core.HtmlText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 轻量 HTML 解析器，基于递归下降法实现。
 */
public class HtmlParser {

    private static final Set<String> VOID_TAGS = Set.of(
            "br", "hr", "img", "input"
    );

    private final String html;
    private final int length;
    private int pos;

    private HtmlParser(String html) {
        this.html = html;
        this.length = html.length();
        this.pos = 0;
    }

    public static HtmlDocument parse(String html) {
        if (html == null || html.isEmpty()) {
            HtmlDocument doc = new HtmlDocument();
            doc.setRootElement(new HtmlElement("div"));
            return doc;
        }
        HtmlParser parser = new HtmlParser(html);
        return parser.parseDocument();
    }

    private HtmlDocument parseDocument() {
        HtmlDocument doc = new HtmlDocument();
        List<HtmlNode> topNodes = new ArrayList<>();

        while (pos < length) {
            skipWhitespace();
            if (pos >= length) break;

            if (startsWithIgnoreCase("<!doctype")) {
                skipUntilChar('>');
                if (pos < length) pos++;
                continue;
            }
            if (startsWith("<!--")) {
                skipComment();
                continue;
            }

            if (peek() == '<') {
                if (pos + 1 < length && html.charAt(pos + 1) == '/') {
                    skipUntilChar('>');
                    if (pos < length) pos++;
                    continue;
                }
                if (startsWithIgnoreCase("<script") || startsWithIgnoreCase("<style")) {
                    skipUnsupportedTag();
                    continue;
                }
                HtmlElement el = parseElement();
                if (el != null) {
                    topNodes.add(el);
                }
            } else {
                String text = parseText();
                if (!text.trim().isEmpty()) {
                    topNodes.add(new HtmlText(text));
                }
            }
        }

        doc.setRootElement(buildRoot(topNodes));
        HtmlElement titleEl = findFirstByTag(doc.getRootElement(), "title");
        if (titleEl != null) {
            StringBuilder sb = new StringBuilder();
            for (HtmlNode child : titleEl.children()) {
                if (child.isText()) {
                    sb.append(((HtmlText) child).text());
                }
            }
            doc.setTitle(sb.toString().trim());
        }
        return doc;
    }

    private HtmlElement buildRoot(List<HtmlNode> topNodes) {
        if (topNodes.isEmpty()) {
            return new HtmlElement("div");
        }
        if (topNodes.size() == 1 && topNodes.get(0).isElement()) {
            return (HtmlElement) topNodes.get(0);
        }
        HtmlElement wrapper = new HtmlElement("div");
        for (HtmlNode node : topNodes) {
            wrapper.addChild(node);
        }
        return wrapper;
    }

    private HtmlElement parseElement() {
        pos++;
        String tagName = parseTagName();
        if (tagName.isEmpty()) return null;
        tagName = tagName.toLowerCase(Locale.ROOT);

        HtmlElement element = new HtmlElement(tagName);
        parseAttributes(element);
        skipWhitespace();
        if (pos < length && peek() == '/') pos++;
        if (pos < length && peek() == '>') pos++;

        if (VOID_TAGS.contains(tagName)) {
            return element;
        }
        parseChildren(element, tagName);
        return element;
    }

    private void parseChildren(HtmlElement parent, String tagName) {
        while (pos < length) {
            if (startsWith("<!--")) {
                skipComment();
                continue;
            }
            if (peek() == '<') {
                if (pos + 1 < length && html.charAt(pos + 1) == '/') {
                    int savePos = pos;
                    pos += 2;
                    String closeTag = parseTagName().toLowerCase(Locale.ROOT);
                    skipWhitespace();
                    if (pos < length && peek() == '>') pos++;

                    if (closeTag.equals(tagName)) {
                        return;
                    }
                    pos = savePos;
                    return;
                }
                if (startsWithIgnoreCase("<script") || startsWithIgnoreCase("<style")) {
                    skipUnsupportedTag();
                    continue;
                }
                HtmlElement child = parseElement();
                if (child != null) {
                    parent.addChild(child);
                }
            } else {
                String text = parseText();
                if (!text.isEmpty()) {
                    parent.addChild(new HtmlText(text));
                }
            }
        }
    }

    private String parseTagName() {
        int start = pos;
        while (pos < length) {
            char c = html.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                pos++;
            } else {
                break;
            }
        }
        return html.substring(start, pos);
    }

    private void parseAttributes(HtmlElement element) {
        while (pos < length) {
            skipWhitespace();
            if (pos >= length) break;
            char c = peek();
            if (c == '>' || c == '/') break;

            String name = parseAttributeName();
            if (name.isEmpty()) {
                pos++;
                continue;
            }
            skipWhitespace();
            String value = "";
            if (pos < length && peek() == '=') {
                pos++;
                skipWhitespace();
                value = parseAttributeValue();
            }
            element.setAttribute(name, value);
        }
    }

    private String parseAttributeName() {
        int start = pos;
        while (pos < length) {
            char c = html.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':') {
                pos++;
            } else {
                break;
            }
        }
        return html.substring(start, pos);
    }

    private String parseAttributeValue() {
        if (pos >= length) return "";
        char quote = peek();
        if (quote == '"' || quote == '\'') {
            pos++;
            int start = pos;
            while (pos < length && peek() != quote) pos++;
            String value = html.substring(start, pos);
            if (pos < length) pos++;
            return decodeEntities(value);
        }
        int start = pos;
        while (pos < length) {
            char c = peek();
            if (c == ' ' || c == '>' || c == '/' || c == '\n' || c == '\r' || c == '\t') break;
            pos++;
        }
        return decodeEntities(html.substring(start, pos));
    }

    private String parseText() {
        StringBuilder sb = new StringBuilder();
        while (pos < length && peek() != '<') {
            char c = peek();
            if (c == '&') {
                sb.append(parseEntity());
            } else {
                sb.append(c);
                pos++;
            }
        }
        return sb.toString();
    }

    private String parseEntity() {
        int start = pos;
        pos++;
        int semi = html.indexOf(';', pos);
        if (semi > 0 && semi - pos <= 10) {
            String entity = html.substring(pos, semi);
            pos = semi + 1;
            return decodeEntity(entity);
        }
        pos = start + 1;
        return "&";
    }

    private String decodeEntities(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i);
                if (semi > 0 && semi - i <= 11) {
                    String entity = s.substring(i + 1, semi);
                    String decoded = decodeEntity(entity);
                    if (decoded != null) {
                        sb.append(decoded);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private String decodeEntity(String entity) {
        if (entity.startsWith("#")) {
            try {
                int codePoint = Integer.parseInt(entity.substring(1));
                return new String(Character.toChars(codePoint));
            } catch (NumberFormatException e) {
                return "&" + entity + ";";
            }
        }
        if (entity.startsWith("x") || entity.startsWith("X")) {
            try {
                int codePoint = Integer.parseInt(entity.substring(1), 16);
                return new String(Character.toChars(codePoint));
            } catch (NumberFormatException e) {
                return "&" + entity + ";";
            }
        }
        return switch (entity) {
            case "lt" -> "<";
            case "gt" -> ">";
            case "amp" -> "&";
            case "quot" -> "\"";
            case "apos" -> "'";
            case "nbsp" -> " ";
            case "middot" -> "·";
            case "bull" -> "•";
            case "lsaquo" -> "‹";
            case "rsaquo" -> "›";
            case "laquo" -> "«";
            case "raquo" -> "»";
            case "darr" -> "↓";
            case "uarr" -> "↑";
            case "harr" -> "↔";
            case "larr" -> "←";
            case "rarr" -> "→";
            case "clubs" -> "♣";
            case "hearts" -> "♥";
            case "diams" -> "♦";
            case "spades" -> "♠";
            case "starf" -> "★";
            case "star" -> "☆";
            case "ldots", "hellip" -> "…";
            default -> "&" + entity + ";";
        };
    }

    private void skipComment() {
        pos += 4;
        int end = html.indexOf("-->", pos);
        pos = end >= 0 ? end + 3 : length;
    }

    private void skipUnsupportedTag() {
        boolean isScript = startsWithIgnoreCase("<script");
        String tagName = isScript ? "script" : "style";
        skipUntilChar('>');
        if (pos < length) pos++;
        String closeTag = "</" + tagName;
        int end = indexOfIgnoreCase(closeTag, pos);
        if (end >= 0) {
            pos = end;
            skipUntilChar('>');
            if (pos < length) pos++;
        } else {
            pos = length;
        }
    }

    private void skipWhitespace() {
        while (pos < length) {
            char c = peek();
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                pos++;
            } else {
                break;
            }
        }
    }

    private void skipUntilChar(char target) {
        while (pos < length && peek() != target) pos++;
    }

    private char peek() {
        return html.charAt(pos);
    }

    private boolean startsWith(String prefix) {
        return html.startsWith(prefix, pos);
    }

    private boolean startsWithIgnoreCase(String prefix) {
        if (pos + prefix.length() > length) return false;
        for (int i = 0; i < prefix.length(); i++) {
            if (Character.toLowerCase(html.charAt(pos + i)) != Character.toLowerCase(prefix.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private int indexOfIgnoreCase(String target, int from) {
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.indexOf(target.toLowerCase(Locale.ROOT), from);
    }

    private HtmlElement findFirstByTag(HtmlElement root, String tag) {
        if (root.tagName().equals(tag)) return root;
        for (HtmlNode child : root.children()) {
            if (child.isElement()) {
                HtmlElement found = findFirstByTag((HtmlElement) child, tag);
                if (found != null) return found;
            }
        }
        return null;
    }
}
