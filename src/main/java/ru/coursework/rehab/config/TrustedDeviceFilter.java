package ru.coursework.rehab.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;
import ru.coursework.rehab.api.TrustedDeviceController;
import ru.coursework.rehab.service.TrustedDeviceService;

import java.io.IOException;

final class TrustedDeviceFilter extends OncePerRequestFilter {
    private final TrustedDeviceService devices;

    TrustedDeviceFilter(TrustedDeviceService devices) {
        this.devices = devices;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/") &&
            !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        var context = SecurityContextHolder.getContext();
        var current = context.getAuthentication();
        if (current != null && current.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_PATIENT"))) {
            // Old password sessions must pass the same device check as a new request.
            var oldSession = request.getSession(false);
            if (oldSession != null) oldSession.invalidate();
            context.setAuthentication(null);
        }
        if (context.getAuthentication() == null && !TrustedDeviceController.isPaused(request)) {
            var cookie = WebUtils.getCookie(request, TrustedDeviceController.COOKIE_NAME);
            if (cookie != null) {
                devices.authenticate(cookie.getValue()).ifPresent(login -> context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(login, null,
                        AuthorityUtils.createAuthorityList("ROLE_PATIENT"))));
            }
        }
        chain.doFilter(request, response);
    }
}
