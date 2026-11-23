package ru.coursework.rehab.service;

import ru.coursework.rehab.domain.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public final class AlertCalculator {
    private AlertCalculator() {}

    public record Window(LocalDate from, LocalDate until, double pain, double wellbeing, double load) {}
    public record Result(String level, String message, Window previous, Window recent) {}

    public static Result calculate(RehabPlan plan, List<Assignment> assignments, List<DiaryEntry> entries,
                                   LocalDate today, AlertSettings settings) {
        if (assignments.isEmpty()) return insufficient("В плане нет назначений.");
        var days = entries.stream()
            .filter(e -> !e.performedOn.isBefore(plan.startsOn) && !e.performedOn.isAfter(plan.endsOn)
                && !e.performedOn.isAfter(today))
            .collect(Collectors.groupingBy(e -> e.performedOn, TreeMap::new, Collectors.toList()));
        int count = settings.daysPerWindow * 2;
        if (days.size() < count) return insufficient("Нужно " + count + " дней с записями, есть " + days.size() + ".");
        var dates = new ArrayList<>(days.keySet());
        var selected = dates.subList(dates.size() - count, dates.size());
        Window previous = window(selected.subList(0, settings.daysPerWindow), days, assignments);
        Window recent = window(selected.subList(settings.daysPerWindow, count), days, assignments);
        double painChange = recent.pain - previous.pain;
        double wellbeingChange = recent.wellbeing - previous.wellbeing;
        double loadChange = recent.load - previous.load;
        List<String> reasons = new ArrayList<>();
        if (painChange + 1e-9 >= settings.deterioration)
            reasons.add(String.format(Locale.ROOT, "Боль выросла с %.1f до %.1f", previous.pain, recent.pain));
        if (-wellbeingChange + 1e-9 >= settings.deterioration)
            reasons.add(String.format(Locale.ROOT, "Самочувствие снизилось с %.1f до %.1f", previous.wellbeing, recent.wellbeing));
        if (!reasons.isEmpty()) return new Result("RED", String.join(". ", reasons), previous, recent);
        if (Math.abs(painChange) < settings.stableBand && Math.abs(wellbeingChange) < settings.stableBand
            && loadChange < settings.loadGrowth)
            return new Result("YELLOW", "Боль и самочувствие почти не меняются, рост выполнения нагрузки ниже порога.",
                previous, recent);
        return new Result("NONE", "Условия уведомления не выполнены.", previous, recent);
    }

    private static Result insufficient(String message) {
        return new Result("INSUFFICIENT", message, null, null);
    }

    private static Window window(List<LocalDate> dates, Map<LocalDate, List<DiaryEntry>> entries,
                                 List<Assignment> assignments) {
        double pain = 0, wellbeing = 0, load = 0;
        for (LocalDate date : dates) {
            List<DiaryEntry> day = entries.get(date);
            pain += day.stream().mapToInt(e -> e.pain).average().orElse(0);
            wellbeing += day.stream().mapToInt(e -> e.wellbeing).average().orElse(0);
            double completed = day.stream().filter(e -> e.status == DiaryEntry.Status.DONE)
                .mapToDouble(e -> Math.min(1, e.actualAmount / e.assignment.targetAmount)).sum();
            load += 100 * completed / assignments.size();
        }
        return new Window(dates.get(0), dates.get(dates.size()-1),
            pain / dates.size(), wellbeing / dates.size(), load / dates.size());
    }
}
