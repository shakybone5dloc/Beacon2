package dev.beacon.application.digest;

import org.springframework.web.util.HtmlUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Turns a {@link Digest} into an email: a subject, an HTML body with inline styles (mail apps
 * ignore stylesheets) and a plain-text body for apps that don't show HTML. Every link goes back
 * into Beacon at {@code appUrl}.
 */
public final class DigestEmail {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a", Locale.US);

    private static final String INK = "#1f2933";
    private static final String MUTED = "#616e7c";
    private static final String RULE = "#e4e7eb";
    private static final String ACCENT = "#2563eb";
    private static final String AMBER = "#9a5b00";

    public record Rendered(String subject, String html, String text) {}

    private DigestEmail() {}

    public static Rendered render(Digest d, String appUrl) {
        var base = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
        return new Rendered(subject(d), html(d, base), text(d, base));
    }

    /** "Beacon: 2 need attention · 1 interview coming up", or a caught-up line. */
    static String subject(Digest d) {
        var parts = new ArrayList<String>();
        if (!d.attention().isEmpty()) parts.add(d.attention().size() + " need" + (d.attention().size() == 1 ? "s" : "") + " attention");
        if (!d.upcoming().isEmpty()) parts.add(d.upcoming().size() + " interview" + (d.upcoming().size() == 1 ? "" : "s") + " coming up");
        if (d.newMatches() > 0) parts.add(d.newMatches() + " new match" + (d.newMatches() == 1 ? "" : "es"));
        return parts.isEmpty() ? "Beacon: your week — all caught up" : "Beacon: " + String.join(" · ", parts);
    }

    private static String html(Digest d, String base) {
        var h = new StringBuilder();
        h.append("<!doctype html><html><body style=\"margin:0;padding:24px;background:#f5f7fa;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:")
                .append(INK).append("\">");
        h.append("<div style=\"max-width:600px;margin:0 auto;background:#ffffff;border:1px solid ").append(RULE)
                .append(";border-radius:8px;padding:24px\">");
        h.append("<h1 style=\"margin:0;font-size:22px\">").append(esc(greeting(d))).append("</h1>");
        h.append("<p style=\"margin:4px 0 0;color:").append(MUTED).append(";font-size:14px\">")
                .append(esc(d.date().format(DATE))).append(" · your week in Beacon</p>");

        if (d.caughtUp()) {
            h.append("<p style=\"margin:20px 0 0;font-size:15px\">You're all caught up. Nothing needs you right now.</p>");
        }

        if (!d.attention().isEmpty()) {
            section(h, "Needs attention", AMBER);
            for (var f : d.attention()) {
                item(h, link(base + "/applications/" + f.applicationId(), f.employer()) + muted(" · " + f.role()),
                        "<span style=\"color:" + AMBER + "\">" + esc(f.reason()) + "</span>");
            }
        }

        if (!d.upcoming().isEmpty()) {
            section(h, "Coming up", INK);
            for (var u : d.upcoming()) {
                var extra = link(base + "/applications/" + u.applicationId() + "/brief", "Brief");
                if (u.cardsDue() > 0) extra += " · " + esc(u.cardsDue() + (u.cardsDue() == 1 ? " card" : " cards") + " to study");
                item(h, link(base + "/applications/" + u.applicationId(), u.employer()) + muted(" · " + u.role()),
                        esc(u.startsAt().format(WHEN)) + " · " + extra);
            }
        }

        if (!d.queue().isEmpty()) {
            section(h, "Queue", INK);
            for (var m : d.queue()) {
                var detail = (m.company() == null ? "Company not given" : m.company())
                        + (m.score() == null ? "" : " · fit " + m.score());
                item(h, link(m.url(), m.title()), esc(detail));
            }
            h.append("<p style=\"margin:8px 0 0;font-size:14px\">")
                    .append(link(base + "/matches", "All " + d.newMatches() + " new " + (d.newMatches() == 1 ? "match" : "matches")))
                    .append("</p>");
        }

        if (!d.decks().isEmpty()) {
            section(h, "Study", INK);
            for (var deck : d.decks()) {
                item(h, esc(deck.name()), link(base + "/study/" + deck.id() + "/session",
                        "Study " + deck.due() + (deck.due() == 1 ? " card" : " cards")));
            }
        }

        var w = d.week();
        section(h, "This week", INK);
        h.append("<p style=\"margin:0;font-size:15px\"><strong>").append(w.sentThisWeek()).append("</strong> sent")
                .append(muted(" (" + w.sentLastWeek() + " last week)"))
                .append(" · <strong>").append(esc(percent(w.responded(), w.applied()))).append("</strong> heard back")
                .append("</p><p style=\"margin:6px 0 0;font-size:14px;color:").append(MUTED).append("\">");
        var stages = new ArrayList<String>();
        for (var s : w.stages()) stages.add(esc(s.label()) + " " + s.count());
        h.append(String.join(" · ", stages)).append(" · ").append(link(base + "/stats", "Stats")).append("</p>");

        if (!d.missingSentResume().isEmpty() || !d.openSuggestions().isEmpty()) {
            section(h, "To-dos", INK);
            for (var t : d.missingSentResume()) {
                item(h, "Record which resume you sent to " + link(base + "/applications/" + t.applicationId(), t.employer()),
                        esc(t.role()));
            }
            for (var t : d.openSuggestions()) {
                item(h, t.count() + " resume " + (t.count() == 1 ? "suggestion" : "suggestions") + " to go for "
                        + link(base + "/applications/" + t.applicationId(), t.employer()), esc(t.role()));
            }
        }

        h.append("<p style=\"margin:28px 0 0;padding-top:16px;border-top:1px solid ").append(RULE)
                .append(";font-size:12px;color:").append(MUTED).append("\">")
                .append(link(base + "/", "Open Beacon")).append(" · ")
                .append(link(base + "/settings/digest", "Change or turn off this email"))
                .append("</p></div></body></html>");
        return h.toString();
    }

    private static String text(Digest d, String base) {
        var t = new ArrayList<String>();
        t.add(greeting(d));
        t.add(d.date().format(DATE) + " · your week in Beacon");
        if (d.caughtUp()) t.add("\nYou're all caught up. Nothing needs you right now.");
        if (!d.attention().isEmpty()) {
            t.add("\nNEEDS ATTENTION");
            d.attention().forEach(f -> t.add("- " + f.employer() + " · " + f.role() + ": " + f.reason()
                    + "\n  " + base + "/applications/" + f.applicationId()));
        }
        if (!d.upcoming().isEmpty()) {
            t.add("\nCOMING UP");
            d.upcoming().forEach(u -> t.add("- " + u.startsAt().format(WHEN) + " · " + u.employer() + " · " + u.role()
                    + (u.cardsDue() > 0 ? " (" + u.cardsDue() + " cards to study)" : "")
                    + "\n  " + base + "/applications/" + u.applicationId() + "/brief"));
        }
        if (!d.queue().isEmpty()) {
            t.add("\nQUEUE (" + d.newMatches() + " new)");
            d.queue().forEach(m -> t.add("- " + m.title() + " · " + (m.company() == null ? "Company not given" : m.company())
                    + (m.score() == null ? "" : " · fit " + m.score()) + "\n  " + m.url()));
        }
        if (!d.decks().isEmpty()) {
            t.add("\nSTUDY");
            d.decks().forEach(deck -> t.add("- " + deck.name() + ": " + deck.due() + " due\n  " + base + "/study/" + deck.id() + "/session"));
        }
        var w = d.week();
        t.add("\nTHIS WEEK");
        t.add(w.sentThisWeek() + " sent (" + w.sentLastWeek() + " last week) · " + percent(w.responded(), w.applied()) + " heard back");
        t.add(String.join(" · ", w.stages().stream().map(s -> s.label() + " " + s.count()).toList()));
        if (!d.missingSentResume().isEmpty() || !d.openSuggestions().isEmpty()) {
            t.add("\nTO-DOS");
            d.missingSentResume().forEach(x -> t.add("- Record which resume you sent to " + x.employer() + " · " + x.role()));
            d.openSuggestions().forEach(x -> t.add("- " + x.count() + " resume suggestions to go for " + x.employer() + " · " + x.role()));
        }
        t.add("\nOpen Beacon: " + base + "/");
        t.add("Change or turn off this email: " + base + "/settings/digest");
        return String.join("\n", t) + "\n";
    }

    private static String greeting(Digest d) {
        return d.name() == null ? "Your week in Beacon" : "Hi " + d.name() + ", here's your week";
    }

    private static void section(StringBuilder h, String title, String color) {
        h.append("<h2 style=\"margin:24px 0 8px;font-size:13px;letter-spacing:0.04em;text-transform:uppercase;color:")
                .append(color).append("\">").append(esc(title)).append("</h2>");
    }

    private static void item(StringBuilder h, String headlineHtml, String detailHtml) {
        h.append("<div style=\"padding:8px 0;border-bottom:1px solid ").append(RULE).append("\">")
                .append("<div style=\"font-size:15px\">").append(headlineHtml).append("</div>")
                .append("<div style=\"font-size:13px;color:").append(MUTED).append(";margin-top:2px\">").append(detailHtml).append("</div>")
                .append("</div>");
    }

    private static String link(String href, String label) {
        return "<a href=\"" + esc(href) + "\" style=\"color:" + ACCENT + ";text-decoration:none\">" + esc(label) + "</a>";
    }

    private static String muted(String text) {
        return "<span style=\"color:" + MUTED + "\">" + esc(text) + "</span>";
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    /** "42%", or "–" when nothing has been applied to. */
    static String percent(long part, long whole) {
        return whole > 0 ? Math.round(part * 100.0 / whole) + "%" : "–";
    }
}
