package com.mtchlvr.hotel.config;

import com.mtchlvr.hotel.repo.UserRepository;
import com.mtchlvr.hotel.service.CaptchaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
            .map(u -> User.withUsername(u.getEmail())
                .password(u.getPasswordHash())
                .roles(u.getRole())
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, CaptchaService captcha) throws Exception {

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/rooms/*/availability").permitAll()
                .requestMatchers(
                    "/", "/rooms", "/register", "/login",
                    "/css/**", "/js/**", "/error", "/favicon.ico", "/api/chat"
                ).permitAll()
                .requestMatchers("/manager/**").hasRole("MANAGER")
                .requestMatchers(
                    "/customer/**", "/reservations/**"
                ).hasRole("CUSTOMER")
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.cacheControl(Customizer.withDefaults()))
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler((request, response, authentication) -> {
                    boolean manager = authentication.getAuthorities().stream()
                        .anyMatch(authority ->
                            authority.getAuthority().equals("ROLE_MANAGER"));
                    String destination = manager ? "/manager" : "/customer/reservations";
                    response.sendRedirect(request.getContextPath() + destination);
                })
                .permitAll()
            )
            .logout(logout -> logout
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setHeader(
                        "Cache-Control",
                        "no-store, no-cache, must-revalidate, max-age=0"
                    );
                    response.setHeader("Pragma", "no-cache");
                    response.setHeader("Expires", "0");
                    response.sendRedirect(request.getContextPath() + "/");
                })
                .permitAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .accessDeniedHandler((request, response, exception) -> {
                    response.sendRedirect(
                        request.getContextPath() + "/login?denied"
                    );
                })
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/chat"));

        http.addFilterBefore(
            new CaptchaLoginFilter(captcha),
            UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }
}
