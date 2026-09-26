package com.main.aqarpaymentbackend.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.customer-issuer}")
    private String customerIssuer;

    @Value("${jwt.admin-issuer}")
    private String adminIssuer;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private Claims getAllClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String issuer = claims.getIssuer();
        String tokenType = claims.get("token_type", String.class);

        boolean customerToken =
                customerIssuer.equals(issuer)
                        && "CUSTOMER".equals(tokenType);

        boolean adminToken =
                adminIssuer.equals(issuer)
                        && "ADMIN".equals(tokenType);

        if (!customerToken && !adminToken) {
            throw new JwtException("Unsupported token issuer or token type");
        }

        return claims;
    }

    public Integer extractUserId(String token) {
        return Integer.valueOf(getAllClaims(token).getId());
    }

    public String extractEmail(String token) {
        return getAllClaims(token).getSubject();
    }

    public Date extractExpiration(String token) {
        return getAllClaims(token).getExpiration();
    }

    public String extractIssuer(String token) {
        return getAllClaims(token).getIssuer();
    }

    public String extractTokenType(String token) {
        return getAllClaims(token).get("token_type", String.class);
    }
}