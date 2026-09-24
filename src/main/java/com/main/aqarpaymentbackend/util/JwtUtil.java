package com.main.aqarpaymentbackend.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration.ms}")
    private Long expirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /// EXTRACT CLAIMS

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(getAllClaims(token));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public Integer extractUserId(String token) {
        return Integer.parseInt(extractClaim(token, Claims::getId));
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }


    /// TOKEN GENERATION

//    public String generateToken(String email ,Integer id) {
//        return Jwts.builder()
//                .issuer("aqar-admin")
//                .claim("token_type", "ADMIN")
//                .subject(email)
//                .id(id.toString())
//                .issuedAt(new Date())
//                .expiration(new Date(System.currentTimeMillis() + expirationMs))
//                .signWith(getSigningKey(), Jwts.SIG.HS256)
//                .compact();
//    }

    public boolean validateToken(String email, UserDetails userDetails, String token) {
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return getAllClaims(token).getExpiration().before(new Date());
    }

    private Claims getAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer("aqar-admin")
                .require("token_type", "ADMIN")
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}