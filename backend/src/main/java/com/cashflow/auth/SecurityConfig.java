package com.cashflow.auth;

import com.cashflow.data.Repositories.Users;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.*;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean ClientRegistrationRepository registrations(GoogleOAuthConfig google) {
        if(!google.enabled()) return key->null;
        return new InMemoryClientRegistrationRepository(google.registration());
    }
    @Bean SecurityFilterChain security(HttpSecurity http,AuthService auth,Users users,
        @Value("${app.allowed-origins}") String origins,@Value("${app.frontend-url}") String frontend,
        GoogleOAuthConfig google,@Value("${app.production}") boolean production,
        @Value("${server.servlet.session.cookie.secure}") boolean secure,@Value("${app.ml-key}") String mlKey) throws Exception {
        if(mlKey.length()<32) throw new IllegalStateException("ML_API_KEY must contain at least 32 characters.");
        if(production && (!secure || !frontend.startsWith("https://"))) throw new IllegalStateException("Production requires HTTPS and secure cookies.");
        var cors=new CorsConfiguration(); var allowed=Arrays.stream(origins.split(",")).map(String::trim).toList();
        if(allowed.stream().anyMatch(s->s.contains("*"))) throw new IllegalStateException("CORS origins must be exact.");
        cors.setAllowedOrigins(allowed); cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET","POST","PATCH","DELETE","OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN"));
        var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",cors);
        http.cors(c->c.configurationSource(source)).csrf(Customizer.withDefaults())
            .authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/me").authenticated()
                .requestMatchers("/api/v1/auth/**","/oauth2/**","/login/oauth2/**","/actuator/health","/error").permitAll()
                .anyRequest().authenticated())
            .sessionManagement(s->s.sessionFixation(f->f.changeSessionId()))
            .requestCache(c->c.disable())
            .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->RequestGuard.fail(s,401,"Sign in to continue."))
                .accessDeniedHandler((r,s,x)->RequestGuard.fail(s,403,"Request blocked. Refresh the page and retry.")))
            .logout(l->l.logoutUrl("/api/v1/auth/logout").invalidateHttpSession(true).clearAuthentication(true)
                .deleteCookies("CFTSESSION").logoutSuccessHandler((r,s,a)->s.setStatus(204)))
            .headers(h->h.contentTypeOptions(Customizer.withDefaults()).frameOptions(f->f.deny())
                .referrerPolicy(r->r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        http.addFilterBefore(new RequestGuard(),CsrfFilter.class);
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain) throws ServletException,IOException {
                var current=SecurityContextHolder.getContext().getAuthentication();
                if(current!=null && current.getPrincipal() instanceof SessionUser principal) {
                    var user=users.findById(principal.id()).orElse(null);
                    if(user==null || !user.verified || user.authVersion!=principal.authVersion()) {
                        if(r.getSession(false)!=null) r.getSession(false).invalidate();
                        SecurityContextHolder.clearContext(); RequestGuard.fail(s,401,"Session expired. Sign in again."); return;
                    }
                }
                chain.doFilter(r,s);
            }
        },AuthorizationFilter.class);
        if(google.enabled()) http.oauth2Login(o->o.successHandler((r,s,a)->{
            try { auth.install(auth.resolveGoogle((OidcUser)a.getPrincipal()),r,s); s.sendRedirect(frontend+"/app"); }
            catch(Exception e) { if(r.getSession(false)!=null) r.getSession(false).invalidate(); SecurityContextHolder.clearContext(); s.sendRedirect(frontend+"/login?oauth=unavailable"); }
        }).failureHandler((r,s,e)->s.sendRedirect(frontend+"/login?oauth=failed")));
        return http.build();
    }
}
