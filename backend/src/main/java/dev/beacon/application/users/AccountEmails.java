package dev.beacon.application.users;

import org.springframework.web.util.HtmlUtils;

/** The password-reset and email-confirmation emails: short, plain, with the link twice (button and text). */
final class AccountEmails {

    record Email(String subject, String html, String text) {}

    private AccountEmails() { }

    static Email passwordReset(String link) {
        return email("Reset your Beacon password",
                "Someone asked to reset the password for your Beacon account. If it was you, choose a new password:",
                "Choose a new password", link,
                "The link works once, within an hour. If you didn't ask, ignore this email: your password stays as it is.");
    }

    static Email confirmEmail(String link) {
        return email("Confirm your email for Beacon",
                "Welcome to Beacon. Confirm this is your email address, so you can reset your password if you ever need to:",
                "Confirm my email", link,
                "The link works for 7 days. If you didn't sign up for Beacon, ignore this email.");
    }

    private static Email email(String subject, String intro, String button, String link, String footer) {
        var href = HtmlUtils.htmlEscape(link);
        var html = """
                <!doctype html><html><body style="margin:0;padding:24px;background:#f5f7fa;\
                font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#141b2b">\
                <div style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:8px;padding:28px">\
                <p style="margin:0 0 16px;font-size:18px;font-weight:600">Beacon</p>\
                <p style="margin:0 0 20px;font-size:15px;line-height:1.5">%s</p>\
                <p style="margin:0 0 20px"><a href="%s" style="display:inline-block;padding:10px 18px;\
                background:#2f6feb;color:#ffffff;border-radius:6px;text-decoration:none;font-size:15px">%s</a></p>\
                <p style="margin:0 0 20px;font-size:13px;color:#424654;line-height:1.5">Or open this link: \
                <a href="%s" style="color:#0055cd;word-break:break-all">%s</a></p>\
                <p style="margin:0;font-size:13px;color:#424654;line-height:1.5">%s</p>\
                </div></body></html>""".formatted(HtmlUtils.htmlEscape(intro), href, HtmlUtils.htmlEscape(button),
                href, href, HtmlUtils.htmlEscape(footer));
        var text = intro + "\n\n" + link + "\n\n" + footer + "\n";
        return new Email(subject, html, text);
    }
}
