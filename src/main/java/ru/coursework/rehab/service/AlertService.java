package ru.coursework.rehab.service;

import jakarta.validation.constraints.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coursework.rehab.domain.*;
import ru.coursework.rehab.repository.*;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AlertService implements CommandLineRunner {
    private final RehabService rehab;
    private final AlertSettingsRepository settings;
    private final PlanRepository plans;
    private final AssignmentRepository assignments;
    private final EntryRepository entries;

    public AlertService(RehabService rehab, AlertSettingsRepository settings, PlanRepository plans,
                        AssignmentRepository assignments, EntryRepository entries) {
        this.rehab = rehab;
        this.settings = settings;
        this.plans = plans;
        this.assignments = assignments;
        this.entries = entries;
    }

    @Override @Transactional
    public void run(String... args) {
        if (!settings.existsById(1L)) settings.save(new AlertSettings());
    }

    private void requireAdmin() {
        if (rehab.actor().role != Account.Role.ADMIN)
            throw new AccessDeniedException("Настройки доступны администратору");
    }

    public SettingsView configuration() {
        requireAdmin();
        return SettingsView.of(settings.findById(1L).orElseThrow());
    }

    @Transactional
    public SettingsView configure(SettingsInput input) {
        requireAdmin();
        if (input.stableBand() > input.deterioration())
            throw BusinessException.invalid("Граница стабильности должна быть не больше порога ухудшения.");
        AlertSettings value = settings.findById(1L).orElseThrow();
        if (value.version != input.version()) throw BusinessException.conflict("Настройки изменены. Обновите страницу.");
        value.daysPerWindow = input.daysPerWindow();
        value.deterioration = input.deterioration();
        value.stableBand = input.stableBand();
        value.loadGrowth = input.loadGrowth();
        settings.flush();
        return SettingsView.of(value);
    }

    public List<PatientAlert> alerts() {
        if (rehab.actor().role == Account.Role.PATIENT)
            throw new AccessDeniedException("Уведомления доступны специалисту и администратору");
        AlertSettings config = settings.findById(1L).orElseThrow();
        var today = rehab.today();
        return rehab.patients().stream().map(patient -> {
            var active = plans.findByPatientIdOrderByIdDesc(patient.id()).stream()
                .filter(plan -> plan.status == RehabPlan.Status.ACTIVE
                    && !today.isBefore(plan.startsOn) && !today.isAfter(plan.endsOn)).findFirst();
            if (active.isEmpty()) return new PatientAlert(patient.id(), patient.fullName(), null,
                new AlertCalculator.Result("INSUFFICIENT", "Нет действующего активного плана.", null, null));
            RehabPlan plan = active.get();
            return new PatientAlert(patient.id(), patient.fullName(), plan.id, AlertCalculator.calculate(plan,
                assignments.findByPlanIdOrderById(plan.id), entries.findByAssignmentPlanIdOrderByPerformedOnDescIdDesc(plan.id),
                today, config));
        }).toList();
    }

    public record PatientAlert(Long patientId, String patientName, Long planId, AlertCalculator.Result result) {}
    public record SettingsView(int daysPerWindow, double deterioration, double stableBand, double loadGrowth, long version) {
        static SettingsView of(AlertSettings s) {
            return new SettingsView(s.daysPerWindow, s.deterioration, s.stableBand, s.loadGrowth, s.version);
        }
    }
    public record SettingsInput(
        @Min(1) @Max(14) int daysPerWindow,
        @DecimalMin("0.1") @DecimalMax("10") double deterioration,
        @DecimalMin("0.1") @DecimalMax("10") double stableBand,
        @DecimalMin("0.1") @DecimalMax("100") double loadGrowth,
        @Min(0) long version) {}
}
