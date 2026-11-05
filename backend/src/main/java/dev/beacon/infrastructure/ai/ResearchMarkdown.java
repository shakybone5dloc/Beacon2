package dev.beacon.infrastructure.ai;

import dev.beacon.application.briefs.BriefException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an AI's web research into the brief's Markdown: its text, a numbered link after each
 * passage the provider says came from a page, and the pages listed at the end. Numbers follow
 * the order sources are first cited.
 */
final class ResearchMarkdown {

    /** A stretch of the reply and the pages it was drawn from (none for the AI's own words). */
    record Cited(String text, List<Source> sources) {}

    record Source(String title, String url) {}

    private ResearchMarkdown() {}

    static String render(List<Cited> parts) {
        var numbers = new LinkedHashMap<String, Integer>();
        var titles = new LinkedHashMap<String, String>();
        var text = new StringBuilder();

        for (var part : parts) {
            var markers = new StringBuilder();
            for (var source : part.sources()) {
                if (source.url() == null || source.url().isBlank()) continue;
                var number = numbers.computeIfAbsent(source.url(), url -> numbers.size() + 1);
                titles.putIfAbsent(source.url(), source.title());
                var marker = " [[" + number + "]](" + source.url() + ")";
                if (markers.indexOf(marker) < 0) markers.append(marker);
            }
            if (markers.isEmpty()) {
                text.append(part.text());
                continue;
            }
            // The marker goes after the cited words, before any line break that ends the part.
            var words = part.text();
            var end = words.length();
            while (end > 0 && Character.isWhitespace(words.charAt(end - 1))) end--;
            text.append(words, 0, end).append(markers).append(words.substring(end));
        }

        if (numbers.isEmpty()) {
            throw new BriefException("The research found nothing it could cite, so there's nothing to add. Try again later.", true);
        }

        var result = new StringBuilder(text.toString().strip()).append("\n\n### Sources\n\n");
        for (Map.Entry<String, Integer> entry : numbers.entrySet()) {
            var title = titles.get(entry.getKey());
            var label = title == null || title.isBlank() ? entry.getKey() : title.strip().replace("]", ")").replace("[", "(");
            result.append(entry.getValue()).append(". [").append(label).append("](").append(entry.getKey()).append(")\n");
        }
        return result.toString().strip();
    }

    /** Parts in order, for a reply whose citations are character ranges into one text (OpenAI's). */
    static List<Cited> fromRanges(String text, List<Range> ranges) {
        var sorted = new ArrayList<>(ranges);
        sorted.sort((a, b) -> Integer.compare(a.end(), b.end()));
        var parts = new ArrayList<Cited>();
        var from = 0;
        var i = 0;
        while (i < sorted.size()) {
            var end = Math.min(Math.max(sorted.get(i).end(), from), text.length());
            var sources = new ArrayList<Source>();
            while (i < sorted.size() && Math.min(Math.max(sorted.get(i).end(), from), text.length()) == end) {
                sources.add(sorted.get(i).source());
                i++;
            }
            parts.add(new Cited(text.substring(from, end), sources));
            from = end;
        }
        parts.add(new Cited(text.substring(from), List.of()));
        return parts;
    }

    /** @param end where the cited passage ends, as a Java string index */
    record Range(int end, Source source) {}
}
