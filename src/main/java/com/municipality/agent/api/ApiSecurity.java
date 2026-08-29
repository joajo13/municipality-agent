package com.municipality.agent.api;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;

/**
 * Who may reach what.
 *
 * <p>Three rules and a deny. The probes are open because whatever is running this has to
 * be able to ask whether it is alive without holding a secret. Everything else under
 * {@code /actuator} needs the key, because metrics say how many residents there are and
 * how much the day cost. The endpoint itself needs the key. Anything not named is denied
 * rather than allowed, so a path added tomorrow is closed until somebody says otherwise.
 *
 * <p>No sessions and no CSRF token, and those two go together: there is no cookie here to
 * ride on, every request carries its own credential, and a CSRF token would be ceremony
 * protecting a browser flow that does not exist.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ApiProperties.class)
public class ApiSecurity {

    /** How long a caller is told to keep using HTTPS once it has used it. */
    private static final Duration HSTS = Duration.ofDays(365);

    @Bean
    ApiKeys apiKeys(ApiProperties properties) {
        return new ApiKeys(properties.key(), properties.allowGeneratedKey());
    }

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http, ApiKeys keys, ApiProperties properties, JsonMapper json) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new ApiKeyFilter(keys), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new RequestSize(properties.maxRequestBytes()), ApiKeyFilter.class)
                .exceptionHandling(handling -> handling.authenticationEntryPoint(saysWhatIsMissing(json)))
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(HSTS.toSeconds()))
                        .frameOptions(Customizer.withDefaults()))
                .build();
    }

    /**
     * A 401 that says which header is missing, in the same problem-detail shape as every
     * other error here. A caller integrating against this should not have to guess.
     */
    private static AuthenticationEntryPoint saysWhatIsMissing(JsonMapper json) {
        return (request, response, failure) -> {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
            problem.setType(URI.create("https://municipality-agent/problems/unauthenticated"));
            problem.setTitle("Unauthenticated");
            problem.setDetail("Send the shared secret in the " + ApiKeyFilter.HEADER + " header.");

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            json.writeValue(response.getOutputStream(), problem);
        };
    }
}
