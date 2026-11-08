package dev.beacon.application.home;

import dev.beacon.contracts.home.HomeTodoResponse;
import dev.beacon.contracts.home.HomeTodosResponse;
import dev.beacon.domain.applications.JobApplication;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What Home shows that no other endpoint already gives: small to-dos across applications.
 * The rest of Home comes from the applications, matches, study and stats endpoints.
 */
@Service
public class HomeService {

    /** Enough to act on without the list taking over the page. */
    static final int LIMIT = 5;

    private static final String MISSING_SENT_RESUME = """
            select a.id, c.name as employer, a.role, 1 as count
            from job_applications a
            left join companies c on c.id = a.company_id
            where a.owner_id = :owner and a.sent_resume_id is null
              and a.status in ('APPLIED', 'INTERVIEWING', 'OFFER')
            order by a.status_changed_at desc
            limit :limit
            """;

    private static final String OPEN_SUGGESTIONS = """
            select a.id, c.name as employer, a.role, count(*) as count
            from job_applications a
            join resume_suggestions s on s.application_id = a.id and s.state = 'OPEN'
            left join companies c on c.id = a.company_id
            where a.owner_id = :owner and a.status = 'SAVED'
            group by a.id, c.name, a.role
            order by max(s.created_at) desc
            limit :limit
            """;

    private static final RowMapper<HomeTodoResponse> TODO = (rs, row) -> new HomeTodoResponse(
            rs.getObject("id", UUID.class),
            rs.getString("employer") == null ? JobApplication.UNDISCLOSED_EMPLOYER : rs.getString("employer"),
            rs.getString("role"), rs.getInt("count"));

    private final NamedParameterJdbcTemplate jdbc;

    public HomeService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public HomeTodosResponse todos(UUID ownerId) {
        var params = Map.of("owner", ownerId, "limit", LIMIT);
        return new HomeTodosResponse(jdbc.query(MISSING_SENT_RESUME, params, TODO),
                jdbc.query(OPEN_SUGGESTIONS, params, TODO));
    }
}
