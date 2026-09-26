package com.main.aqarpaymentbackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.aqarpaymentbackend.util.JwtUtil;
import com.main.aqarpaymentbackend.util.ReturnObject;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {

            Integer userId = jwtUtil.extractUserId(token);
            String role = jwtUtil.extractTokenType(token);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );

            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());

            clearAuthCookie(response);
            writeError(response, HttpStatus.UNAUTHORIZED, "Your session has expired. Please Login again.");
            return;
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());

            clearAuthCookie(response);
            writeError(response, HttpStatus.UNAUTHORIZED, "Your session is invalid. Please Login again.");
            return;
        }

        filterChain.doFilter(request, response);
    }


    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if ("Authorization".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }


    private void clearAuthCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("Authorization", "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);

        response.addCookie(cookie);
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            String message
    ) throws IOException {

        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ReturnObject body = new ReturnObject(message, false, null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

}
