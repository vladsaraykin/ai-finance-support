package com.finance.support.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return login -> users.find(login).orElseThrow(() -> new UsernameNotFoundException("Неверный логин или пароль."));
    }

    @Bean
    @ConditionalOnWebApplication
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http.cors(cors -> {}).csrf(csrf -> {})
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/csrf", "/api/auth/login").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            mapper.writeValue(response.getWriter(), java.util.Map.of("message", "Требуется вход."));
                        })
                        .accessDeniedHandler((request, response, error) -> {
                            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                            boolean expired = (auth == null || !auth.isAuthenticated()
                                    || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                                    && !request.getRequestURI().equals(request.getContextPath() + "/api/auth/login");
                            response.setStatus(expired ? 401 : 403);
                            response.setContentType("application/json;charset=UTF-8");
                            mapper.writeValue(response.getWriter(), java.util.Map.of("message", expired
                                    ? "Требуется вход." : "Запрос отклонён. Обновите страницу и повторите."));
                        }))
                .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            var user = (FinanceUser) authentication.getPrincipal();
                            mapper.writeValue(response.getWriter(), java.util.Map.of("id", user.id(), "login", user.getUsername()));
                        })
                        .failureHandler((request, response, error) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            mapper.writeValue(response.getWriter(), java.util.Map.of("message", "Неверный логин или пароль."));
                        }))
                .logout(logout -> logout.logoutUrl("/api/auth/logout").logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .addFilterBefore(new LoginRateLimiter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
