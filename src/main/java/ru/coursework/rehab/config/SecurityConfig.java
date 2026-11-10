package ru.coursework.rehab.config;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.coursework.rehab.repository.AccountRepository;
@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(AccountRepository accounts) {
        return login->accounts.findByUsername(login).map(a->User.withUsername(a.username).password(a.passwordHash).roles(a.role.name()).disabled(a.deletedAt!=null).build())
            .orElseThrow(()->new UsernameNotFoundException("Пользователь не найден"));
    }
    @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a->a.requestMatchers(org.springframework.http.HttpMethod.POST,"/api/auth/register").permitAll().requestMatchers("/","/index.html","/app.js","/style.css","/favicon.svg",
            "/api/auth/csrf","/api/auth/login","/swagger-ui.html","/swagger-ui/**","/v3/api-docs/**","/error").permitAll().anyRequest().authenticated());
        http.csrf(c->c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
        http.formLogin(f->f.loginProcessingUrl("/api/auth/login")
            .successHandler((req,res,auth)->{res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"authenticated\":true}");})
            .failureHandler((req,res,ex)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"detail\":\"Неверный логин или пароль\"}");}));
        http.logout(l->l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,auth)->res.setStatus(204)));
        http.exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"detail\":\"Войдите в систему\"}");})
            .accessDeniedHandler((req,res,ex)->{res.setStatus(403);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"detail\":\"Недостаточно прав или истёк токен формы\"}");}));
        http.headers(h->h.contentTypeOptions(c->{}).frameOptions(f->f.deny()));
        return http.build();
    }
}
