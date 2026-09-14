package com.ledgerflow.security;

import com.ledgerflow.tenancy.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes both halves of a request's identity from one token parse: who
 * is calling, and which organization they are calling for. Keeping them
 * together avoids verifying the signature twice per request.
 *
 * The tenant is cleared in a finally block. Servlet containers pool threads,
 * so a context left behind would be inherited by whatever request runs next
 * on that thread -- and it would look like a working request reading another
 * organization's data.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parseClaims(token);
                String username = jwtService.extractUsername(claims);
                String authority = "ROLE_" + jwtService.extractRole(claims).name();

                var authentication = new UsernamePasswordAuthenticationToken(
                        username, null, List.of(new SimpleGrantedAuthority(authority)));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                TenantContext.set(jwtService.extractOrgId(claims));
            } catch (JwtException e) {
                // Leave the SecurityContext unauthenticated; the request falls
                // through to Spring Security's normal 401 handling below.
                SecurityContextHolder.clearContext();
                TenantContext.clear();
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
