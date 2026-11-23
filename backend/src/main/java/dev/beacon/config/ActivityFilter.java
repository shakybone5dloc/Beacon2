package dev.beacon.config;

import dev.beacon.application.users.ActivityTracker;
import dev.beacon.application.users.SignedInUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Notes that a signed-in user used Beacon, after the request: that way a sign-in or sign-up in
 * this request counts too. Never fails the request. Not a Spring bean, so it runs only inside the
 * security filter chain.
 */
class ActivityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ActivityFilter.class);

    private final ActivityTracker activity;

    ActivityFilter(ActivityTracker activity) {
        this.activity = activity;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SignedInUser user) {
            try {
                activity.seen(user.id());
            } catch (RuntimeException ex) {
                log.warn("Couldn't note activity: {}", ex.getMessage());
            }
        }
    }
}
