package ru.coursework.rehab.service;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import ru.coursework.rehab.domain.*;
/** Показатели регулярности и фактической нагрузки. Не оценивают степень выздоровления. */
public final class ProgressCalculator {
    private ProgressCalculator() {}
    public record Point(LocalDate date,double amount,double target,int pain,int wellbeing) {}
    public record Series(Long assignmentId,String title,Exercise.Unit unit,double targetAmount,List<Point> points) {}
    public record Result(long scheduled,long done,long skipped,long unrecorded,long targetReached,
        double adherence,double targetAchievement,List<Series> series) {}
    public static Result calculate(RehabPlan plan,List<Assignment> items,List<DiaryEntry> entries,LocalDate today) {
        LocalDate until=today.isBefore(plan.endsOn)?today:plan.endsOn;
        if(plan.closedOn!=null && plan.closedOn.isBefore(until)) until=plan.closedOn;
        long days=plan.status==RehabPlan.Status.DRAFT || until.isBefore(plan.startsOn)?0:ChronoUnit.DAYS.between(plan.startsOn,until)+1;
        long scheduled=days*items.size();
        LocalDate limit=until;
        List<DiaryEntry> eligible=entries.stream().filter(e->!e.performedOn.isBefore(plan.startsOn)&&!e.performedOn.isAfter(limit)).toList();
        long done=eligible.stream().filter(e->e.status==DiaryEntry.Status.DONE).count();
        long skipped=eligible.stream().filter(e->e.status==DiaryEntry.Status.SKIPPED).count();
        long reached=eligible.stream().filter(e->e.status==DiaryEntry.Status.DONE&&e.actualAmount>=e.assignment.targetAmount).count();
        List<Series> series=items.stream().map(a->new Series(a.id,a.title,a.unit,a.targetAmount,
            eligible.stream().filter(e->e.assignment.id.equals(a.id)&&e.status==DiaryEntry.Status.DONE)
                .sorted(Comparator.comparing(e->e.performedOn))
                .map(e->new Point(e.performedOn,e.actualAmount,a.targetAmount,e.pain,e.wellbeing)).toList())).toList();
        return new Result(scheduled,done,skipped,Math.max(0,scheduled-done-skipped),reached,
            scheduled==0?0:100.0*done/scheduled,scheduled==0?0:100.0*reached/scheduled,series);
    }
}
