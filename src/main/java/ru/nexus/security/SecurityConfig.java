package ru.nexus.security;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain chain(
    HttpSecurity h,
    ru.nexus.service.NexusService service
  ) throws Exception {
    return h
      .csrf(c -> c.disable())
      .sessionManagement(s ->
        s.sessionCreationPolicy(
          org.springframework.security.config.http.SessionCreationPolicy.STATELESS
        )
      )
      .addFilterBefore(
        new org.springframework.web.filter.OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            jakarta.servlet.FilterChain chain
          ) throws java.io.IOException, jakarta.servlet.ServletException {
            String path = request.getRequestURI();
            if (
              path.startsWith("/api/v1/") &&
              !(
                request.getMethod().equals("GET") &&
                path.matches("/api/v1/(avatars|photos)/[0-9]+")
              ) &&
              !java.util.Set.of(
                "/api/v1/health",
                "/api/v1/auth/register",
                "/api/v1/auth/login"
              ).contains(path)
            ) {
              try {
                Long id = service.identify(request.getHeader("Authorization"));
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                  new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    id,
                    null,
                    java.util.List.of()
                  )
                );
              } catch (
                org.springframework.web.server.ResponseStatusException ex
              ) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response
                  .getWriter()
                  .write("{\"status\":401,\"message\":\"Войдите в аккаунт\"}");
                return;
              }
            }
            chain.doFilter(request, response);
          }
        },
        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class
      )
      .authorizeHttpRequests(a ->
        a
          .requestMatchers(
            "/api/v1/health",
            "/api/v1/auth/register",
            "/api/v1/auth/login"
          )
          .permitAll()
          .requestMatchers(
            org.springframework.http.HttpMethod.GET,
            "/api/v1/avatars/*",
            "/api/v1/photos/*"
          )
          .permitAll()
          .requestMatchers("/api/**")
          .authenticated()
          .anyRequest()
          .permitAll()
      )
      .exceptionHandling(e ->
        e.authenticationEntryPoint((request, response, ex) -> {
          response.setStatus(401);
          response.setContentType("application/json");
          response
            .getWriter()
            .write("{\"status\":401,\"message\":\"Authentication required\"}");
        })
      )
      .build();
  }
}
