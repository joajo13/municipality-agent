package com.municipality.agent.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the shared secret off the request and, if it is the right one, says so.
 *
 * <p>It only ever authenticates. A request without a key, or with the wrong one, is left
 * exactly as it arrived and the filter chain decides what that means — which keeps the
 * question of what is public in one place, next to everything else that is configured
 * about the chain, rather than spread between here and there.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Api-Key";

    /** The name every request authenticated this way is carrying. There is only one caller. */
    private static final String CHANNEL = "channel";

    private final ApiKeys keys;

    public ApiKeyFilter(ApiKeys keys) {
        this.keys = keys;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        @Nullable String offered = request.getHeader(HEADER);

        if (keys.accepts(offered)) {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                            CHANNEL, null, AuthorityUtils.createAuthorityList("ROLE_CHANNEL")));
        }

        chain.doFilter(request, response);
    }
}
