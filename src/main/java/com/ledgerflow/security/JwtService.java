package com.ledgerflow.security;

import com.ledgerflow.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private static final String ROLE_CLAIM = "role";
    private static final String ORG_CLAIM = "org";

    private final SecretKey signingKey;
    private final long expirationMinutes;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-minutes}") long expirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * The organization is a signed claim rather than a client-supplied header:
     * the tenant a request acts for must not be something the caller can
     * change at will. Switching organizations means getting a new token.
     */
    public String generateToken(String username, Role role, Long orgId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(ROLE_CLAIM, role.name())
                .claim(ORG_CLAIM, orgId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * @throws JwtException if the token is malformed, expired, or has an
     *                       invalid signature.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(Claims claims) {
        return claims.getSubject();
    }

    public Role extractRole(Claims claims) {
        return Role.valueOf(claims.get(ROLE_CLAIM, String.class));
    }

    public Long extractOrgId(Claims claims) {
        Number orgId = claims.get(ORG_CLAIM, Number.class);
        return orgId == null ? null : orgId.longValue();
    }
}
