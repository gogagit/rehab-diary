package ru.coursework.rehab;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.coursework.rehab.domain.*;
import ru.coursework.rehab.service.AlertCalculator;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AlertCalculatorTest {
    @Test void comparesTwoPeriodsOfRecordedDaysAndHonorsConfiguredThresholds() {
        RehabPlan plan = new RehabPlan();
        plan.startsOn = LocalDate.of(2026, 1, 1);
        plan.endsOn = plan.startsOn.plusDays(20);
        Assignment exercise = new Assignment();
        exercise.targetAmount = 10;
        AlertSettings settings = new AlertSettings();
        List<DiaryEntry> entries = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            DiaryEntry e = new DiaryEntry();
            e.assignment = exercise;
            e.performedOn = plan.startsOn.plusDays(i * 2);
            e.status = DiaryEntry.Status.DONE;
            e.pain = i < 3 ? 2 : 4;
            e.wellbeing = 7;
            e.actualAmount = 5;
            entries.add(e);
        }
        var assignments = List.of(exercise);
        assertEquals("INSUFFICIENT", AlertCalculator.calculate(plan, assignments, entries, plan.startsOn.plusDays(9), settings).level());
        assertEquals("RED", AlertCalculator.calculate(plan, assignments, entries, plan.endsOn, settings).level());
        settings.deterioration = 3;
        assertEquals("NONE", AlertCalculator.calculate(plan, assignments, entries, plan.endsOn, settings).level());
        entries.forEach(e -> e.pain = 2);
        assertEquals("YELLOW", AlertCalculator.calculate(plan, assignments, entries, plan.endsOn, settings).level());
        entries.subList(3, 6).forEach(e -> e.actualAmount = 8);
        assertEquals("NONE", AlertCalculator.calculate(plan, assignments, entries, plan.endsOn, settings).level());
    }
}
