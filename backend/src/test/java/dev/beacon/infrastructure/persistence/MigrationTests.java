package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.companies.CompanyNames;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Migrations that rewrite existing rows, checked against data written by the previous version. */
@Testcontainers
class MigrationTests {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Test
    void v2BackfillsStatusHistoryForExistingApplications() {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var jdbc = new JdbcTemplate(dataSource);

        flyway(dataSource, "1").migrate();
        jdbc.update("""
                INSERT INTO job_applications (id, company, role, status, version, created_at, updated_at) VALUES
                  ('00000000-0000-0000-0000-000000000001', 'Acme',   'Engineer', 'SAVED',        0, '2026-10-01T12:00:00Z', '2026-10-02T12:00:00Z'),
                  ('00000000-0000-0000-0000-000000000002', 'Globex', 'Engineer', 'INTERVIEWING', 2, '2026-10-01T12:00:00Z', '2026-10-09T12:00:00Z')
                """);

        flyway(dataSource, "2").migrate();

        assertThat(jdbc.queryForList(
                "SELECT company, (status_changed_at AT TIME ZONE 'UTC')::text AS changed FROM job_applications ORDER BY company"))
                .extracting(row -> row.get("company") + " " + row.get("changed"))
                .containsExactly("Acme 2026-10-01 12:00:00", "Globex 2026-10-09 12:00:00");

        assertThat(jdbc.queryForList("""
                SELECT a.company, c.from_status, c.to_status
                FROM application_status_changes c JOIN job_applications a ON a.id = c.application_id
                ORDER BY a.company, c.changed_at
                """))
                .extracting(row -> row.get("company") + " " + row.get("from_status") + "->" + row.get("to_status"))
                .containsExactly("Acme null->SAVED", "Globex null->SAVED", "Globex SAVED->INTERVIEWING");
    }

    @Test
    void v7TurnsCompanyNamesIntoCompaniesPerUser() {
        var dataSource = schema("v7_backfill");
        var jdbc = new JdbcTemplate(dataSource);
        flyway(dataSource, "v7_backfill", "6").migrate();

        var alice = user(jdbc, "alice@example.com");
        var bob = user(jdbc, "bob@example.com");
        application(jdbc, alice, "Acme, Inc.", "2026-10-01T12:00:00Z");
        application(jdbc, alice, "ACME", "2026-10-02T12:00:00Z");
        application(jdbc, alice, "  acme   inc ", "2026-10-03T12:00:00Z");
        application(jdbc, alice, "Globex Corporation", "2026-10-04T12:00:00Z");
        application(jdbc, alice, "Unknown company", "2026-10-05T12:00:00Z");
        application(jdbc, alice, "   ", "2026-10-06T12:00:00Z");
        application(jdbc, bob, "Acme", "2026-10-07T12:00:00Z");
        var legacy = application(jdbc, null, "Old Co", "2026-09-01T12:00:00Z");
        jdbc.update("""
                INSERT INTO documents (id, application_id, kind, filename, content_type, size_bytes, content, created_at)
                VALUES (gen_random_uuid(), ?::uuid, 'OTHER', 'old.txt', 'text/plain', 1, 'x'::bytea, now())
                """, legacy);

        flyway(dataSource, "v7_backfill", "7").migrate();

        assertThat(jdbc.queryForList("""
                SELECT u.email, c.name, c.normalized_name, c.type FROM companies c JOIN users u ON u.id = c.owner_id
                ORDER BY u.email, c.normalized_name
                """))
                .extracting(row -> row.get("email") + " " + row.get("name") + " -> " + row.get("normalized_name") + " " + row.get("type"))
                .containsExactly(
                        "alice@example.com Acme, Inc. -> acme EMPLOYER",
                        "alice@example.com Globex Corporation -> globex EMPLOYER",
                        "bob@example.com Acme -> acme EMPLOYER");

        assertThat(jdbc.queryForList("""
                SELECT a.created_at, c.name FROM job_applications a LEFT JOIN companies c ON c.id = a.company_id
                ORDER BY a.created_at
                """))
                .extracting(row -> String.valueOf(row.get("name")))
                .containsExactly("Acme, Inc.", "Acme, Inc.", "Acme, Inc.", "Globex Corporation", "null", "null", "Acme");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM job_applications WHERE owner_id IS NULL", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents", Long.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'job_applications' AND column_name = 'company'
                """, Long.class)).isZero();
    }

    /** The backfill's SQL and CompanyNames.normalize must agree, or find-or-create would make duplicates. */
    @Test
    void v7NormalizesNamesExactlyLikeTheJavaCode() {
        var dataSource = schema("v7_normalize");
        var jdbc = new JdbcTemplate(dataSource);
        flyway(dataSource, "v7_normalize", "6").migrate();

        var names = List.of("Acme, Inc.", "ACME", "acme  inc", "Globex Corporation", "Initech LLC", "Hooli Ltd.",
                "Pied Piper Co", "Co", "Soylent Corp.", "Vandelay Industries", "Wayne Enterprises, Incorporated",
                "Stark Industries PLC", "Müller GmbH", "  Tyrell   Corp  ", "Inc", "Dunder Mifflin Paper Company");
        for (var name : names) {
            application(jdbc, user(jdbc, "user-" + UUID.randomUUID() + "@example.com"), name, "2026-10-01T12:00:00Z");
        }

        flyway(dataSource, "v7_normalize", "7").migrate();

        var byName = new HashMap<String, String>();
        jdbc.query("SELECT name, normalized_name FROM companies",
                (RowCallbackHandler) rs -> byName.put(rs.getString("name"), rs.getString("normalized_name")));
        for (var name : names) {
            assertThat(byName.get(name.strip())).as(name).isEqualTo(CompanyNames.normalize(name));
        }
    }

    private static UUID user(JdbcTemplate jdbc, String email) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, created_at) VALUES (?, ?, '{noop}x', now())", id, email);
        return id;
    }

    private static String application(JdbcTemplate jdbc, UUID owner, String company, String createdAt) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO job_applications (id, owner_id, company, role, status, version, created_at, updated_at, status_changed_at)
                VALUES (?, ?, ?, 'Engineer', 'SAVED', 0, ?::timestamptz, ?::timestamptz, ?::timestamptz)
                """, id, owner, company, createdAt, createdAt, createdAt);
        return id.toString();
    }

    /** Its own schema, so tests sharing the container don't see each other's migrations. */
    private static DriverManagerDataSource schema(String name) {
        return new DriverManagerDataSource(postgres.getJdbcUrl() + "&currentSchema=" + name,
                postgres.getUsername(), postgres.getPassword());
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).target(target).load();
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        return Flyway.configure().dataSource(dataSource).target(target).load();
    }
}
