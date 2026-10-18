package com.schoolsphere.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolsphere.auth.internal.JwtService;
import com.schoolsphere.auth.internal.UserRepository;
import com.schoolsphere.tenants.api.TenantsApi;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // DelegatingPasswordEncoder (bcrypt by default), spec 8.1.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtService jwtService,
                                           UserRepository userRepository,
                                           TenantsApi tenantsApi,
                                           CorsConfigurationSource corsConfigurationSource,
                                           ObjectMapper objectMapper) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, userRepository);
        SchoolContextFilter schoolContextFilter = new SchoolContextFilter(tenantsApi, objectMapper);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh", "/api/v1/invitations/**").permitAll()
                        .requestMatchers("/api/v1/openapi.json", "/api/v1/docs", "/api/v1/docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers("/api/v1/sync/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED, "unauthenticated",
                                        "Authentication is required."))
                        .accessDeniedHandler((request, response, deniedException) ->
                                writeProblem(response, objectMapper, HttpStatus.FORBIDDEN, "forbidden",
                                        "You do not have permission for this action.")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(schoolContextFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    private static void writeProblem(HttpServletResponse response, ObjectMapper mapper, HttpStatus status,
                                     String code, String detail) throws java.io.IOException {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setType(URI.create("https://schoolsphere.dev/errors/" + code));
        pd.setTitle(status.getReasonPhrase());
        pd.setDetail(detail);
        pd.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), pd);
    }
}
