package dev.beacon.infrastructure.postings;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small helpers shared by the job source adapters. */
final class Feeds {

    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);");
    private static final int SNIPPET_LENGTH = 240;

    private Feeds() {}

    /** Text value of a field, or null when it is missing, null or blank. */
    static String text(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        var text = value.asString().strip();
        return text.isEmpty() ? null : text;
    }

    /** Positive whole number from a numeric or numeric-string field; null otherwise (0 means "not given" in several feeds). */
    static Integer positiveInt(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        Double number = value.isNumber() ? Double.valueOf(value.asDouble()) : parseDouble(value.asString());
        return number != null && number > 0 ? (int) Math.round(number) : null;
    }

    static List<String> strings(JsonNode node, String field) {
        var list = new ArrayList<String>();
        for (var item : node.path(field).values()) {
            var text = item.asString().strip();
            if (!text.isEmpty()) list.add(text);
        }
        return list;
    }

    /** Plain text from HTML, with common entities decoded and whitespace collapsed. */
    static String plain(String html) {
        if (html == null) return null;
        var text = TAGS.matcher(html).replaceAll(" ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&rsquo;", "'")
                .replace("&hellip;", "…");
        text = NUMERIC_ENTITY.matcher(text).replaceAll(m -> {
            var code = m.group(1);
            var codePoint = code.startsWith("x") ? Integer.parseInt(code.substring(1), 16) : Integer.parseInt(code);
            return Matcher.quoteReplacement(Character.toString(codePoint));
        });
        text = SPACES.matcher(text).replaceAll(" ").strip();
        return text.isEmpty() ? null : text;
    }

    /** First few sentences of an HTML description as plain text. */
    static String snippet(String html) {
        var text = plain(html);
        if (text == null || text.length() <= SNIPPET_LENGTH) return text;
        var cut = text.lastIndexOf(' ', SNIPPET_LENGTH);
        return text.substring(0, cut > 0 ? cut : SNIPPET_LENGTH) + "…";
    }

    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.strip());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
