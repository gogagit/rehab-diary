package ru.coursework.rehab;
import org.junit.jupiter.api.Test;
import ru.coursework.rehab.domain.*;
import ru.coursework.rehab.service.ProgressCalculator;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class ProgressCalculatorTest {
    private final LocalDate today=LocalDate.of(2026,11,2);
    private RehabPlan plan() { RehabPlan p=new RehabPlan();p.startsOn=today.minusDays(2);p.endsOn=today.plusDays(4);p.status=RehabPlan.Status.ACTIVE;return p; }
    private Assignment item() { Assignment a=new Assignment();a.id=1L;a.title="Ходьба";a.unit=Exercise.Unit.MINUTES;a.targetAmount=20;return a; }
    private DiaryEntry entry(Assignment a,int days,DiaryEntry.Status status,double amount) {
        DiaryEntry e=new DiaryEntry();e.assignment=a;e.performedOn=today.minusDays(days);e.status=status;e.actualAmount=amount;return e;
    }
    @Test void elapsedDaysOnlyAndPartialLoadCountsAsDone() {
        RehabPlan p=plan();Assignment a=item();
        var r=ProgressCalculator.calculate(p,List.of(a),List.of(entry(a,2,DiaryEntry.Status.DONE,10),entry(a,1,DiaryEntry.Status.SKIPPED,0)),today);
        assertThat(r.scheduled()).isEqualTo(3);assertThat(r.done()).isEqualTo(1);assertThat(r.skipped()).isEqualTo(1);assertThat(r.unrecorded()).isEqualTo(1);
        assertThat(r.adherence()).isCloseTo(100.0/3,within(.001));assertThat(r.targetReached()).isZero();assertThat(r.series().get(0).points()).hasSize(1);
    }
    @Test void draftHasNoScheduledDays() { RehabPlan p=plan();p.status=RehabPlan.Status.DRAFT;assertThat(ProgressCalculator.calculate(p,List.of(item()),List.of(),today).scheduled()).isZero(); }
    @Test void futurePlanHasNoScheduledDays() { RehabPlan p=plan();p.startsOn=today.plusDays(1);assertThat(ProgressCalculator.calculate(p,List.of(item()),List.of(),today).scheduled()).isZero(); }
    @Test void earlyCompletionFreezesDenominator() { RehabPlan p=plan();p.status=RehabPlan.Status.COMPLETED;p.closedOn=today.minusDays(1);assertThat(ProgressCalculator.calculate(p,List.of(item()),List.of(),today.plusDays(20)).scheduled()).isEqualTo(2); }
    @Test void endDateCapsDenominator() { RehabPlan p=plan();assertThat(ProgressCalculator.calculate(p,List.of(item()),List.of(),today.plusDays(20)).scheduled()).isEqualTo(7); }
    @Test void targetAchievementDoesNotMixUnits() { Assignment a=item();var r=ProgressCalculator.calculate(plan(),List.of(a),List.of(entry(a,0,DiaryEntry.Status.DONE,20)),today);assertThat(r.targetReached()).isEqualTo(1);assertThat(r.series().get(0).unit()).isEqualTo(Exercise.Unit.MINUTES); }
}
