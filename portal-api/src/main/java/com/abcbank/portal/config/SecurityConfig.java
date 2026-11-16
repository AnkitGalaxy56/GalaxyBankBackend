package com.abcbank.portal.config;

import com.abcbank.portal.security.JwtFilter;
import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        .requestMatchers(HttpMethod.POST,
                                "/auth/login", "/auth/verify-otp").permitAll()

                        .requestMatchers(HttpMethod.GET,
                                "/themes", "/themes/**", "/banks/*/theme").permitAll()

                        .requestMatchers(HttpMethod.GET,
                                "/dashboard/bank", "/dashboard/bank/**")
                                .hasAnyAuthority(
                                        "ROLE_BANK_ADMIN",
                                        "ROLE_BANK_USER",
                                        "ROLE_ADMIN",
                                        "ROLE_SUPER_ADMIN"
                                )

                        .requestMatchers(HttpMethod.GET,
                                "/dashboard/super-admin", "/dashboard/super-admin/**")
                                .hasAnyAuthority(
                                        "ROLE_SUPER_ADMIN",
                                        "ROLE_ADMIN"
                                )

                        .requestMatchers(HttpMethod.POST, "/themes").hasAnyAuthority("ROLE_BANK_ADMIN", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/themes/**").hasAnyAuthority("ROLE_BANK_ADMIN", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/themes/**").hasAnyAuthority("ROLE_BANK_ADMIN", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:3000"
        ));

        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin"
        ));

        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}