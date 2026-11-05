package com.abcbank.mobile_banking.config;

import com.abcbank.mobile_banking.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(a -> a
            .requestMatchers("/api/v1/registration/start", "/api/v1/registration/verify-otp", "/actuator/health").permitAll()
            // LOGIN + FORGOT MPIN (added)
            .requestMatchers("/api/v1/auth/web/login", "/api/v1/auth/mpin/login",
                             "/api/v1/auth/forgot-mpin/request", "/api/v1/auth/forgot-mpin/verify-otp").permitAll()
            .requestMatchers("/api/v1/auth/forgot-mpin/reset").hasAuthority("SCOPE_mpin:reset")
            .requestMatchers("/api/v1/registration/complete").hasAuthority("SCOPE_registration:complete")
            // only a real login token may use banking APIs (registration / reset tokens may not)
            .requestMatchers("/api/v1/transfers/**").hasAuthority("SCOPE_access")
            .anyRequest().authenticated())
          .httpBasic(b -> b.disable()).formLogin(f -> f.disable())
          .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class).build();
    }
    @Bean PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(12); }
}
