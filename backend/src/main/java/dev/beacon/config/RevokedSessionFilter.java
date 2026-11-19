package dev.beacon.config;

import dev.beacon.application.users.SessionRevocations;
import dev.beacon.application.users.SignedInUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Signs out a session that signed in before its user's password was reset: the session is
 * invalidated and the request goes on signed out, so it gets a 401. Not a Spring bean, so it runs
 * only inside the security filter chain.
 */
class RevokedSessionFilter extends OncePerRequestFilter {

    private final SessionRevocations revocations;

    RevokedSessionFilter(SessionRevocations revocations) {
        this.revocations = revocations;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var session = request.getSession(false);
        if (session != null && authentication != null && authentication.getPrincipal() instanceof SignedInUser user) {
            // Sessions signed back in from a remember-me cookie don't record the time; their start will do.
            var signedInAt = session.getAttribute(SessionRevocations.SIGNED_IN_AT) instanceof Instant at
                    ? at : Instant.ofEpochMilli(session.getCreationTime());
            if (revocations.isRevoked(user.id(), signedInAt)) {
                session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
