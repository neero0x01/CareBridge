package com.carebridge.config;

import com.carebridge.common.error.ApiError;
import com.carebridge.common.error.ErrorCode;
import com.carebridge.security.JwtAuthenticationFilter;
import com.carebridge.security.MustChangePasswordFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(CarebridgeProperties.class)
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final MustChangePasswordFilter mustChangePasswordFilter;
  private final CarebridgeProperties properties;
  private final ObjectMapper objectMapper;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      MustChangePasswordFilter mustChangePasswordFilter,
      CarebridgeProperties properties,
      ObjectMapper objectMapper) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.mustChangePasswordFilter = mustChangePasswordFilter;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/register-tenant")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/inbound")
                    .permitAll()
                    .requestMatchers("/api/v1/public/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (request, response, authException) ->
                            writeError(
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                ErrorCode.UNAUTHORIZED,
                                "Unauthorized"))
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) ->
                            writeError(
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                ErrorCode.FORBIDDEN,
                                "Forbidden")))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(mustChangePasswordFilter, JwtAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    List<String> origins =
        Arrays.stream(properties.getCors().getAllowedOrigins().split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  private void writeError(
      HttpServletResponse response, int status, ErrorCode code, String message) {
    try {
      response.setStatus(status);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectMapper.writeValue(response.getOutputStream(), ApiError.of(code, message, ""));
    } catch (Exception e) {
      response.setStatus(status);
    }
  }
}
