package com.mtchlvr.hotel.config;

import com.mtchlvr.hotel.repo.UserRepository;
import com.mtchlvr.hotel.service.CaptchaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
                .requestMatchers(
                    "/", "/rooms", "/register", "/login",
                    "/css/**", "/js/**", "/error", "/favicon.ico", "/api/chat", "/api/rooms/**"
                ).permitAll()
                .requestMatchers("/manager/**").hasRole("MANAGER")
                .requestMatchers(
                    "/customer/**", "/reservations/**"
                ).hasRole("CUSTOMER")
                .anyRequest().authenticated()
            )
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
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/chat"));

        http.addFilterBefore(
            new CaptchaLoginFilter(captcha),
            UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }
}
