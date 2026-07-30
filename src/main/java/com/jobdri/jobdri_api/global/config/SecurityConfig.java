package com.jobdri.jobdri_api.global.config;

import com.jobdri.jobdri_api.domain.auth.handler.OAuth2AuthenticationFailureHandler;
import com.jobdri.jobdri_api.domain.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.jobdri.jobdri_api.domain.auth.service.CustomOAuth2UserService;
import com.jobdri.jobdri_api.global.apiPayload.exception.handler.CustomAccessDeniedHandler;
import com.jobdri.jobdri_api.global.apiPayload.exception.handler.CustomAuthenticationEntryPoint;
import com.jobdri.jobdri_api.global.jwt.JwtAuthenticationFilter;
import com.jobdri.jobdri_api.global.jwt.JwtUtil;
import com.jobdri.jobdri_api.global.metrics.AuthRedisMetricsRecorder;
import com.jobdri.jobdri_api.global.security.UserDetailsServiceImpl;
import com.jobdri.jobdri_api.global.logging.RequestContextLoggingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final StringRedisTemplate redisTemplate;
    private final AuthRedisMetricsRecorder authRedisMetricsRecorder;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, userDetailsService, redisTemplate, authRedisMetricsRecorder);
    }

    @Bean
    public RequestContextLoggingFilter requestContextLoggingFilter(
            @Value("${app.logging.request-id-max-length:64}") int requestIdMaxLength,
            @Value("${app.logging.trusted-proxies:}") List<String> trustedProxies
    ) {
        return new RequestContextLoggingFilter(requestIdMaxLength, trustedProxies);
    }

    @Bean
    public FilterRegistrationBean<RequestContextLoggingFilter> requestContextLoggingFilterRegistration(
            RequestContextLoggingFilter requestContextLoggingFilter
    ) {
        FilterRegistrationBean<RequestContextLoggingFilter> registration = new FilterRegistrationBean<>(requestContextLoggingFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RequestContextLoggingFilter requestContextLoggingFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {

        http.cors((cors) -> cors.configurationSource(corsConfigurationSource()));

        http.csrf((csrf) -> csrf.disable());

        http.sessionManagement((session) ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

        http.securityContext((context) ->
                context.securityContextRepository(new RequestAttributeSecurityContextRepository())
        );

        http.authorizeHttpRequests((authorize) -> authorize
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/", "/health-check").permitAll()
                .requestMatchers("/").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/internal/worker/**").permitAll()
                .requestMatchers("/api/payments/toss/callback").permitAll()
                .requestMatchers("/api/payments/portone/webhook").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
        );

        http.oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(oAuth2AuthenticationSuccessHandler)
                .failureHandler(oAuth2AuthenticationFailureHandler)
        );

        http.addFilterBefore(requestContextLoggingFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(jwtAuthenticationFilter, RequestContextLoggingFilter.class);

        http.exceptionHandling((exceptions) -> exceptions
                .authenticationEntryPoint(customAuthenticationEntryPoint)
                .accessDeniedHandler(customAccessDeniedHandler)
        );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOrigins(java.util.List.of(
                "https://jobdri.site",
                "https://www.jobdri.site",
                "https://jobdri.com",
                "https://www.jobdri.com",
                "https://api.jobdri.site",
                "https://job-dri.vercel.app",
                "http://localhost:5173",
                "http://localhost:8080",
                "http://localhost:3000"));
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(java.util.List.of("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(java.util.List.of("Authorization", "Location"));

        var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
