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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


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
                        .requestMatchers(HttpMethod.POST,
                                "/fawaterk/oauth/token",
                                "/webhooks/fawaterak/paid_json",
                                "/webhooks/fawaterak/failed_json",
                                "/paymob/auth/token").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/fawaterk/getPaymentMethods",
                                "/fawaterk/success",
                                "/fawaterk/fail",
                                "/fawaterk/pending",
                                "/fawaterk/getTransactionData").permitAll()
                        .requestMatchers("/paymob/dues/{dueId}/createIntention").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/excel/import")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/dues", "/dues/{dueId}")
                        .hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        /// Add token validation filter before reaching the UsernamePasswordAuthenticationFilter that check the user identity from database (We use it when jwt didn't authenticated to login again with the credintials)
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("https://urgent-moistness-trifle.ngrok-free.dev"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
