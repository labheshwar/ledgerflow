package com.ledgerflow.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/signup").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // The whole point of a tokenized invoice link is that
                        // the customer holding it has no LedgerFlow account
                        // to authenticate with. The token itself, 32 random
                        // bytes, is what stands in for a role check here.
                        .requestMatchers(HttpMethod.GET, "/public/**").permitAll()
                        // "/transactions/**" rather than the bare path, so
                        // posting a reversal at /transactions/{id}/reverse
                        // needs ADMIN exactly as posting the original did.
                        .requestMatchers(HttpMethod.POST, "/transactions/**").hasRole("ADMIN")
                        // Editing the chart of accounts changes how every
                        // figure in the business is classified, so it is an
                        // administrator's job. Reading it is not.
                        .requestMatchers(HttpMethod.POST, "/accounts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/accounts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/accounts/**").hasRole("ADMIN")
                        // Locking a period or posting the year-end close
                        // changes what every past figure is allowed to mean.
                        .requestMatchers(HttpMethod.POST, "/periods/**").hasRole("ADMIN")
                        // Reference data an invoice or bill will point at --
                        // reading it is everyone's job, editing it is not.
                        .requestMatchers(HttpMethod.POST, "/contacts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/contacts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/contacts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/tax-rates/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/tax-rates/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/tax-rates/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/items/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/items/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/items/**").hasRole("ADMIN")
                        // Creating, sending or voiding an invoice moves real
                        // money through the ledger; reading one does not.
                        .requestMatchers(HttpMethod.POST, "/invoices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/invoices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/invoices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/bills/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/bills/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/bills/**").hasRole("ADMIN")
                        // No PUT/DELETE here at all: a payment is never edited, only
                        // voided by reversal, the same POST /{id}/void every other
                        // reversible document already uses.
                        .requestMatchers(HttpMethod.POST, "/payments/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/bank-accounts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/bank-accounts/**").hasRole("ADMIN")
                        .anyRequest().hasAnyRole("ADMIN", "VIEWER"))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
