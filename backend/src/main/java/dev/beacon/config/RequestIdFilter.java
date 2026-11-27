package dev.beacon.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request an id: the caller's {@code X-Request-Id} when it looks safe, else a new
 * one. It goes in the logging MDC as {@code requestId}, back in the response header, and into
 * error bodies (see {@code RequestIdProblemAdvice}). First in the security chain, so every
 * line logged for the request carries it. Not a Spring bean.
 */
class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    static final String MDC_KEY = "requestId";

    /** Short and plain, so a caller can't forge log lines or fill the log with junk. */
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var incoming = request.getHeader(HEADER);
        var id = incoming != null && SAFE.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
