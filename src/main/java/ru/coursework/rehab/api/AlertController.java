package ru.coursework.rehab.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.coursework.rehab.service.AlertService;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AlertController {
    private final AlertService alerts;
    public AlertController(AlertService alerts) { this.alerts = alerts; }

    @GetMapping("/alerts")
    public List<AlertService.PatientAlert> alerts() { return alerts.alerts(); }

    @GetMapping("/alert-settings")
    public AlertService.SettingsView settings() { return alerts.configuration(); }

    @PutMapping("/alert-settings")
    public AlertService.SettingsView configure(@Valid @RequestBody AlertService.SettingsInput input) {
        return alerts.configure(input);
    }
}
