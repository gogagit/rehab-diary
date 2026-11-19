package ru.coursework.rehab.config;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.coursework.rehab.repository.AccountRepository;
import ru.coursework.rehab.domain.Account;
import ru.coursework.rehab.api.TrustedDeviceController;
import ru.coursework.rehab.service.TrustedDeviceService;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import java.time.Duration;
@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(AccountRepository accounts) {
        return login->accounts.findByUsername(login).filter(a -> a.role != Account.Role.PATIENT).map(a->User.withUsername(a.username).password(a.passwordHash).roles(a.role.name()).disabled(a.deletedAt!=null).build())
            .orElseThrow(()->new UsernameNotFoundException("Пользователь не найден"));
    }
    @Bean SecurityFilterChain filterChain(HttpSecurity http, TrustedDeviceService devices) throws Exception {
        http.authorizeHttpRequests(a->a.requestMatchers(org.springframework.http.HttpMethod.POST,"/api/auth/register","/api/auth/device","/api/auth/device/resume").permitAll().requestMatchers("/","/index.html","/app.js","/style.css","/favicon.svg",
            "/api/auth/csrf","/api/auth/login","/swagger-ui.html","/swagger-ui/**","/v3/api-docs/**","/error").permitAll().anyRequest().authenticated());
        http.addFilterBefore(new TrustedDeviceFilter(devices), AnonymousAuthenticationFilter.class);
        http.csrf(c->c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
        http.formLogin(f->f.loginProcessingUrl("/api/auth/login")
            .successHandler((req,res,auth)->{res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"authenticated\":true}");})
            .failureHandler((req,res,ex)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"detail\":\"Неверный логин или пароль\"}");}));
        http.logout(l->l.logoutUrl("/api/auth/logout")
            .logoutSuccessHandler((req,res,auth)->{
                String device = TrustedDeviceController.deviceToken(req);
                if ("true".equals(req.getParameter("forget"))) {
                    devices.forget(device);
                    res.addHeader("Set-Cookie", TrustedDeviceController.cookie("",Duration.ZERO,req.isSecure()));
                    res.addHeader("Set-Cookie", TrustedDeviceController.pauseCookie(false,req.isSecure()));
                } else if (device != null) {
                    res.addHeader("Set-Cookie", TrustedDeviceController.pauseCookie(true,req.isSecure()));
                }
                res.setStatus(204);
            }));
        http.exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"detail\":\"Войдите в систему\"}");})
            .accessDeniedHandler((req,res,ex)->{res.setStatus(403);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"detail\":\"Недостаточно прав или истёк токен формы\"}");}));
        http.headers(h->h.contentTypeOptions(c->{}).frameOptions(f->f.deny()));
        return http.build();
    }
}
