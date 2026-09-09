package com.agroclima.api.core.config;

import com.agroclima.api.core.security.JwtAuthenticationEntryPoint;
import com.agroclima.api.core.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;
import java.util.regex.Pattern;

/** Espelha o CORSMiddleware + get_current_user de app/main.py e app/core/security.py. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppProperties appProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    public SecurityConfig(
            AppProperties appProperties,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.appProperties = appProperties;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/registro",
                                "/api/v1/auth/recuperar-senha",
                                "/api/v1/auth/redefinir-senha").permitAll()
                        // Chamado pela propria Telegram Bot API, nunca pelo frontend -- nao tem
                        // Bearer token pra exigir (feature 017, contracts/telegram.md).
                        .requestMatchers(HttpMethod.POST, "/api/v1/telegram/webhook").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = appProperties.cors().origins();
        String regexProperty = appProperties.cors().originRegex();
        Pattern originPattern = (regexProperty != null && !regexProperty.isBlank())
                ? Pattern.compile(regexProperty)
                : null;

        // CorsConfiguration.addAllowedOriginPattern() so aceita glob (com "*"), nao regex --
        // CORS_ORIGIN_REGEX precisa de regex de verdade (previews do Vercel), entao a
        // fonte e construida manualmente por requisicao em vez de usar UrlBasedCorsConfigurationSource.
        return request -> {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowCredentials(true);
            config.setAllowedMethods(List.of("*"));
            config.setAllowedHeaders(List.of("*"));

            String origin = request.getHeader("Origin");
            if (origin != null
                    && (allowedOrigins.contains(origin) || (originPattern != null && originPattern.matcher(origin).matches()))) {
                config.setAllowedOrigins(List.of(origin));
            }
            return config;
        };
    }
}
