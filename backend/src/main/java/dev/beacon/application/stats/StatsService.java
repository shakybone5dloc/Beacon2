package dev.beacon.application.stats;

import dev.beacon.contracts.stats.StageTimeResponse;
import dev.beacon.contracts.stats.StatsGroupResponse;
import dev.beacon.contracts.stats.StatsResponse;
import dev.beacon.contracts.stats.WeekCountResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * The job search in numbers (A3): how far applications get, by source and by resume, how
 * long each stage takes, and how many go out each week. Every count comes from the status
 * history, so an application that was interviewed and later rejected still counts as
 * interviewed. Reporting queries, so plain SQL rather than JPA.
 */
@Service
public class StatsService {

    /** Statuses that mean the application was sent, and what each later step counts as. */
    static final List<String> APPLIED = List.of("APPLIED", "INTERVIEWING", "OFFER", "ACCEPTED", "DECLINED", "REJECTED");
    static final List<String> RESPONDED = List.of("INTERVIEWING", "OFFER", "ACCEPTED", "DECLINED", "REJECTED");
    static final List<String> INTERVIEWED = List.of("INTERVIEWING", "OFFER", "ACCEPTED", "DECLINED");
    static final List<String> OFFERED = List.of("OFFER", "ACCEPTED", "DECLINED");
    /** The stages an application waits in; the closed ones are where it ends. */
    static final List<String> STAGES = List.of("SAVED", "APPLIED", "INTERVIEWING", "OFFER");

    /** No word this long after applying counts as ghosted. */
    static final Duration GHOSTED_AFTER = Duration.ofDays(21);
    static final int WEEKS = 12;
    static final String NOT_RECORDED = "Not recorded";

    /**
     * One row of flags per application (what it ever reached), then totalled three ways at
     * once with GROUPING SETS: overall, by source, and by the resume its fit was checked against.
     */
    private static final String GROUPS = """
            with apps as (
                select a.id,
                       coalesce(nullif(trim(a.source), ''), 'Not recorded') as source,
                       coalesce(rp.label, 'Not recorded') as resume,
                       a.status, a.status_changed_at
                from job_applications a
                left join application_fits af on af.application_id = a.id
                left join fit_scores fs on fs.id = af.fit_score_id
                left join resume_profiles rp on rp.document_id = fs.resume_document_id
                where a.owner_id = :owner and a.created_at >= :since
            ),
            flags as (
                select a.id, a.source, a.resume,
                       bool_or(c.to_status in (:applied)) as applied,
                       bool_or(c.to_status in (:responded)) as responded,
                       bool_or(c.to_status in (:interviewed)) as interviewed,
                       bool_or(c.to_status in (:offered)) as offered,
                       bool_and(a.status = 'APPLIED' and a.status_changed_at < :ghostedBefore) as ghosted,
                       min(c.changed_at) filter (where c.to_status in (:applied)) as applied_at,
                       min(c.changed_at) filter (where c.to_status in (:responded)) as responded_at
                from apps a
                join application_status_changes c on c.application_id = a.id
                group by a.id, a.source, a.resume
            )
            select grouping(source) as all_sources, grouping(resume) as all_resumes, source, resume,
                   count(*) as total,
                   count(*) filter (where applied) as applied,
                   count(*) filter (where responded) as responded,
                   count(*) filter (where interviewed) as interviewed,
                   count(*) filter (where offered) as offered,
                   count(*) filter (where ghosted) as ghosted,
                   percentile_cont(0.5) within group (
                       order by extract(epoch from responded_at - applied_at) / 86400)
                       filter (where responded_at >= applied_at) as median_days
            from flags
            group by grouping sets ((), (source), (resume))
            """;

    /**
     * Each status change paired with the next one for the same application (LEAD), so the
     * time between them is how long that stage took; the last one is the stage it's in now.
     */
    private static final String STAGE_TIMES = """
            with spans as (
                select c.to_status as stage, c.changed_at,
                       lead(c.changed_at) over (partition by c.application_id order by c.changed_at, c.id) as left_at
                from application_status_changes c
                join job_applications a on a.id = c.application_id
                where a.owner_id = :owner and a.created_at >= :since
            )
            select stage,
                   count(left_at) as moved,
                   count(*) - count(left_at) as current_count,
                   avg(extract(epoch from left_at - changed_at) / 86400) as average_days,
                   percentile_cont(0.5) within group (order by extract(epoch from left_at - changed_at) / 86400)
                       as median_days
            from spans
            where stage in (:stages)
            group by stage
            """;

    /** When each application was first sent, counted by the week it falls in, in the user's zone. */
    private static final String WEEKLY = """
            select cast(date_trunc('week', first_applied at time zone :zone) as date) as week, count(*) as applied
            from (
                select c.application_id, min(c.changed_at) as first_applied
                from application_status_changes c
                join job_applications a on a.id = c.application_id
                where a.owner_id = :owner and c.to_status in (:applied)
                group by c.application_id
            ) firsts
            where first_applied >= :from
            group by week
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public StatsService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @param since only applications created from then; null for all of them */
    @Transactional(readOnly = true)
    public StatsResponse stats(UUID ownerId, Instant since, ZoneId zone) {
        var now = Instant.now(clock);
        var params = new MapSqlParameterSource()
                .addValue("owner", ownerId)
                .addValue("since", utc(since == null ? Instant.EPOCH : since))
                .addValue("ghostedBefore", utc(now.minus(GHOSTED_AFTER)))
                .addValue("applied", APPLIED)
                .addValue("responded", RESPONDED)
                .addValue("interviewed", INTERVIEWED)
                .addValue("offered", OFFERED)
                .addValue("stages", STAGES);

        StatsGroupResponse overall = null;
        var bySource = new ArrayList<StatsGroupResponse>();
        var byResume = new ArrayList<StatsGroupResponse>();
        for (var row : jdbc.query(GROUPS, params, StatsService::groupRow)) {
            if (row.allSources() && row.allResumes()) overall = row.group(null);
            else if (!row.allSources()) bySource.add(row.group(row.source()));
            else byResume.add(row.group(row.resume()));
        }
        // Most interviews first, then most sent; "Not recorded" last.
        Comparator<StatsGroupResponse> best = Comparator
                .comparing((StatsGroupResponse g) -> NOT_RECORDED.equals(g.name()))
                .thenComparing(StatsGroupResponse::interviewed, Comparator.reverseOrder())
                .thenComparing(StatsGroupResponse::applied, Comparator.reverseOrder())
                .thenComparing(StatsGroupResponse::name);
        bySource.sort(best);
        byResume.sort(best);

        return new StatsResponse(since,
                overall != null ? overall : new StatsGroupResponse(null, 0, 0, 0, 0, 0, 0, null),
                bySource, byResume, stages(params), weeks(ownerId, zone, now));
    }

    private List<StageTimeResponse> stages(MapSqlParameterSource params) {
        var found = new HashMap<String, StageTimeResponse>();
        jdbc.query(STAGE_TIMES, params, rs -> {
            var stage = rs.getString("stage");
            found.put(stage, new StageTimeResponse(stage, rs.getLong("moved"), rs.getLong("current_count"),
                    days(rs, "average_days"), days(rs, "median_days")));
        });
        return STAGES.stream()
                .map(stage -> found.getOrDefault(stage, new StageTimeResponse(stage, 0, 0, null, null)))
                .toList();
    }

    /** The last twelve weeks, Monday to Sunday in the user's zone, with empty weeks filled in. */
    private List<WeekCountResponse> weeks(UUID ownerId, ZoneId zone, Instant now) {
        var thisWeek = LocalDate.ofInstant(now, zone).with(DayOfWeek.MONDAY);
        var firstWeek = thisWeek.minusWeeks(WEEKS - 1);
        var params = new MapSqlParameterSource()
                .addValue("owner", ownerId)
                .addValue("zone", zone.getId())
                .addValue("applied", APPLIED)
                .addValue("from", utc(firstWeek.atStartOfDay(zone).toInstant()));
        var counts = new HashMap<LocalDate, Long>();
        jdbc.query(WEEKLY, params, rs -> {
            counts.put(rs.getObject("week", LocalDate.class), rs.getLong("applied"));
        });
        var weeks = new ArrayList<WeekCountResponse>();
        for (var week = firstWeek; !week.isAfter(thisWeek); week = week.plusWeeks(1)) {
            weeks.add(new WeekCountResponse(week, counts.getOrDefault(week, 0L)));
        }
        return weeks;
    }

    private record GroupRow(boolean allSources, boolean allResumes, String source, String resume, long total,
                            long applied, long responded, long interviewed, long offered, long ghosted,
                            Double medianDays) {
        StatsGroupResponse group(String name) {
            return new StatsGroupResponse(name, total, applied, responded, interviewed, offered, ghosted, medianDays);
        }
    }

    private static GroupRow groupRow(ResultSet rs, int rowNum) throws SQLException {
        return new GroupRow(rs.getInt("all_sources") == 1, rs.getInt("all_resumes") == 1,
                rs.getString("source"), rs.getString("resume"), rs.getLong("total"), rs.getLong("applied"),
                rs.getLong("responded"), rs.getLong("interviewed"), rs.getLong("offered"), rs.getLong("ghosted"),
                days(rs, "median_days"));
    }

    /** Days to one decimal place; null when the column is (averages come back as numeric). */
    private static Double days(ResultSet rs, String column) throws SQLException {
        var value = rs.getDouble(column);
        return rs.wasNull() ? null : Math.round(value * 10) / 10.0;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
