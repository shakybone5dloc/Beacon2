package dev.beacon.application.users;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Everything a user owns, for taking it with them (export) or removing it for good (deletion),
 * L3. Both work table by table from {@link #EXPORTS}; a test checks every table in the database
 * is either listed there or in {@link #NOT_EXPORTED}, so a table added later can't be forgotten.
 */
@Service
public class AccountData {

    /**
     * What goes in the export: one JSON file per entry, from a query whose rows are the user's
     * ({@code :owner} is their id). Secrets are left out: the password hash, and the API keys,
     * which are listed by provider only.
     */
    static final Map<String, String> EXPORTS = new LinkedHashMap<>();

    static {
        EXPORTS.put("users", """
                select id, email, display_name, created_at, ai_monthly_budget_usd, send_from_email
                from users where id = :owner""");
        EXPORTS.put("user_api_keys", """
                select provider, hint, created_at, updated_at from user_api_keys where user_id = :owner""");
        EXPORTS.put("digest_settings", "select * from digest_settings where user_id = :owner");
        EXPORTS.put("companies", "select * from companies where owner_id = :owner");
        EXPORTS.put("people", "select * from people where owner_id = :owner");
        EXPORTS.put("job_applications", "select * from job_applications where owner_id = :owner");
        EXPORTS.put("application_status_changes", """
                select s.* from application_status_changes s
                join job_applications a on a.id = s.application_id where a.owner_id = :owner""");
        EXPORTS.put("application_people", """
                select l.* from application_people l
                join job_applications a on a.id = l.application_id where a.owner_id = :owner""");
        EXPORTS.put("contacts", "select * from contacts where owner_id = :owner");
        EXPORTS.put("interviews", "select * from interviews where owner_id = :owner");
        EXPORTS.put("interview_people", """
                select l.* from interview_people l
                join interviews i on i.id = l.interview_id where i.owner_id = :owner""");
        // The files themselves go in documents/, so the listing leaves their bytes out.
        EXPORTS.put("documents", """
                select id, application_id, kind, filename, content_type, size_bytes, created_at
                from documents where owner_id = :owner""");
        EXPORTS.put("resume_profiles", "select * from resume_profiles where owner_id = :owner");
        EXPORTS.put("fit_scores", "select * from fit_scores where owner_id = :owner");
        EXPORTS.put("application_fits", """
                select f.* from application_fits f
                join job_applications a on a.id = f.application_id where a.owner_id = :owner""");
        EXPORTS.put("saved_searches", "select * from saved_searches where owner_id = :owner");
        EXPORTS.put("search_runs", """
                select r.* from search_runs r
                join saved_searches s on s.id = r.saved_search_id where s.owner_id = :owner""");
        EXPORTS.put("job_matches", "select * from job_matches where owner_id = :owner");
        EXPORTS.put("interview_briefs", "select * from interview_briefs where owner_id = :owner");
        EXPORTS.put("brief_sections", """
                select s.* from brief_sections s
                join interview_briefs b on b.id = s.brief_id where b.owner_id = :owner""");
        EXPORTS.put("stories", "select * from stories where owner_id = :owner");
        EXPORTS.put("study_decks", "select * from study_decks where owner_id = :owner");
        EXPORTS.put("study_cards", "select * from study_cards where owner_id = :owner");
        EXPORTS.put("study_reviews", """
                select r.* from study_reviews r
                join study_cards c on c.id = r.card_id where c.owner_id = :owner""");
        EXPORTS.put("resume_suggestions", "select * from resume_suggestions where owner_id = :owner");
        EXPORTS.put("ai_usage", "select * from ai_usage where owner_id = :owner");
        EXPORTS.put("invite_codes", "select * from invite_codes where created_by = :owner");
    }

    /** Tables that hold nothing of a user's: Flyway's own record of migrations. */
    static final Set<String> NOT_EXPORTED = Set.of("flyway_schema_history");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AccountData(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Writes the user's data as a zip: a README, one JSON file per kind of data, and every file
     * they uploaded or saved under documents/. Read in one transaction, so it's a consistent copy.
     */
    @Transactional(readOnly = true)
    public void export(UUID ownerId, OutputStream out) throws IOException {
        var zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        put(zip, "README.txt", readme(Instant.now(clock)));
        for (var entry : EXPORTS.entrySet()) {
            var sql = "select coalesce(json_agg(row_to_json(x)), '[]'::json)::text from ("
                    + entry.getValue().replace(":owner", "?") + ") x";
            var json = jdbc.queryForObject(sql, String.class, ownerId);
            put(zip, "data/" + entry.getKey() + ".json", json);
        }
        jdbc.query("select id, filename, content from documents where owner_id = ? order by created_at",
                rows -> {
                    try (InputStream content = rows.getBinaryStream("content")) {
                        zip.putNextEntry(new ZipEntry("documents/" + rows.getString("id") + "-"
                                + safeName(rows.getString("filename"))));
                        content.transferTo(zip);
                        zip.closeEntry();
                    } catch (IOException ex) {
                        throw new java.io.UncheckedIOException(ex);
                    }
                }, ownerId);
        zip.finish();
    }

    /**
     * Removes the user and everything they own. Almost every table goes with the user by its
     * foreign key; AI usage has none (it's kept even when the call that made it failed, see V14),
     * so it's deleted first. Invite codes they made stay, with no creator.
     */
    @Transactional
    public void delete(UUID ownerId) {
        jdbc.update("delete from ai_usage where owner_id = ?", ownerId);
        jdbc.update("delete from users where id = ?", ownerId);
    }

    private static void put(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** Keeps a filename to letters, digits, dots, dashes and underscores, for any unzip tool. */
    static String safeName(String filename) {
        var cleaned = filename == null ? "" : filename.replaceAll("[^A-Za-z0-9._-]+", "_");
        cleaned = cleaned.replaceAll("_+\\.", ".").replaceAll("^[._]+", "");
        if (cleaned.length() > 120) cleaned = cleaned.substring(cleaned.length() - 120);
        return cleaned.isEmpty() ? "file" : cleaned;
    }

    private static String readme(Instant now) {
        return """
                Your Beacon data, exported %s.

                data/       One JSON file per kind of data: your applications, contacts, interviews,
                            stories, study cards and the rest. Each row is as Beacon stores it.
                            data/users.json is your account; data/user_api_keys.json lists which
                            providers you saved keys for, without the keys.
                documents/  Every file you uploaded or Beacon saved for you (resumes, cover
                            letters, posting PDFs), named by the document's id and its filename,
                            matching data/documents.json.

                Not included: your password, and your API keys themselves.
                """.formatted(now);
    }
}
