package ru.coursework.rehab.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.util.WebUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.coursework.rehab.service.BusinessException;
import ru.coursework.rehab.service.TrustedDeviceService;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TrustedDeviceController {
    public static final String COOKIE_NAME = "rehab_device";
    private static final String PAUSE_COOKIE = "rehab_device_paused";
    private final TrustedDeviceService devices;

    public TrustedDeviceController(TrustedDeviceService devices) {
        this.devices = devices;
    }

    @PostMapping("/patients/{patientId}/device-invite")
    public ResponseEntity<Map<String, String>> invite(@PathVariable Long patientId, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Map.of("token", devices.issue(patientId, authentication)));
    }

    @GetMapping("/patients/{patientId}/devices")
    public ResponseEntity<TrustedDeviceService.DeviceList> list(@PathVariable Long patientId, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(devices.list(patientId, authentication));
    }

    @DeleteMapping("/patients/{patientId}/devices/{deviceId}")
    public ResponseEntity<Void> revoke(@PathVariable Long patientId, @PathVariable Long deviceId, Authentication authentication) {
        devices.revoke(patientId, deviceId, authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/patients/{patientId}/device-invite")
    public ResponseEntity<Void> cancelInvite(@PathVariable Long patientId, Authentication authentication) {
        devices.cancelInvite(patientId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/device")
    public ResponseEntity<Void> pair(@Valid @RequestBody PairInput input, HttpServletRequest request,
                                     Authentication authentication) {
        if (authentication != null) throw BusinessException.conflict("Сначала выйдите из текущего кабинета");
        String name = input.name() == null || input.name().isBlank() ? browserName(request) : input.name().strip();
        String token = devices.pair(input.token(), name, deviceToken(request));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE, cookie(token, TrustedDeviceService.DEVICE_LIFETIME, request.isSecure()),
                pauseCookie(false, request.isSecure()))
            .build();
    }

    @PostMapping("/auth/device/resume")
    public ResponseEntity<Void> resume(HttpServletRequest request, Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .noneMatch(role -> role.getAuthority().equals("ROLE_PATIENT"))) {
            throw BusinessException.conflict("Сначала выйдите из кабинета сотрудника");
        }
        if (devices.authenticate(deviceToken(request)).isEmpty()) {
            throw new BusinessException(401, "Откройте новую ссылку для входа, которую выдал администратор.");
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE, pauseCookie(false, request.isSecure())).build();
    }

    public static boolean isPaused(HttpServletRequest request) {
        return WebUtils.getCookie(request, PAUSE_COOKIE) != null;
    }

    public static String pauseCookie(boolean paused, boolean secure) {
        return ResponseCookie.from(PAUSE_COOKIE, paused ? "1" : "").path("/").httpOnly(true)
            .secure(secure).sameSite("Strict").maxAge(paused ? TrustedDeviceService.DEVICE_LIFETIME : Duration.ZERO)
            .build().toString();
    }

    private String browserName(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        if (agent == null) return "Браузер";
        String platform = agent.contains("Android") ? "Android" : agent.contains("iPhone") ? "iPhone" :
            agent.contains("iPad") ? "iPad" : agent.contains("Windows") ? "Windows" :
            agent.contains("Macintosh") ? "macOS" : agent.contains("Linux") ? "Linux" : "";
        String browser = agent.contains("Edg") ? "Edge" : agent.contains("Firefox") || agent.contains("FxiOS") ? "Firefox" :
            agent.contains("Chrome") || agent.contains("CriOS") ? "Chrome" : agent.contains("Safari") ? "Safari" : "Браузер";
        return platform.isEmpty() ? browser : browser + " · " + platform;
    }

    public static String deviceToken(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, COOKIE_NAME);
        return cookie == null ? null : cookie.getValue();
    }

    public static String cookie(String value, Duration lifetime, boolean secure) {
        return ResponseCookie.from(COOKIE_NAME, value).path("/").httpOnly(true)
            .secure(secure).sameSite("Strict").maxAge(lifetime).build().toString();
    }

    public record PairInput(String token, @Size(max = 80) String name) {}
}
