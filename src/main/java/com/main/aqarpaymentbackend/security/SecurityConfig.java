package com.main.aqarpaymentbackend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Fawaterk uses its signature, not an admin or customer JWT
                        .requestMatchers(HttpMethod.POST, "/fawaterk/auth/token", "/fawaterk/webhook").permitAll()
                        .requestMatchers(HttpMethod.POST, "/excel/import", "/fawaterk/getTransactionData")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/dues", "/dues/{dueId}")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/fawaterk/dues/{dueId}/checkout")
                        .hasRole("CUSTOMER")
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        /// Add token validation filter before reaching the UsernamePasswordAuthenticationFilter that check the user identity from database (We use it when jwt didn't authenticated to login again with the credintials)
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
