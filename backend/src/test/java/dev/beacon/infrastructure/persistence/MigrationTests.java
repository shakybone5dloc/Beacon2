package dev.beacon.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

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

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        return Flyway.configure().dataSource(dataSource).target(target).load();
    }
}
