package ru.coursework.rehab.api;
import ru.coursework.rehab.domain.*;
import java.time.*;
import java.util.List;
public final class Views {
    private Views() {}
    public record UserView(Long id,String username,String fullName,Account.Role role) {
        public static UserView of(Account a) { return new UserView(a.id,a.username,a.fullName,a.role); }
    }
    public record PatientView(Long id,String fullName,LocalDate birthDate,String rehabilitationReason,
        String notes,Long specialistId,String specialistName,PlanView finishedCourse) {
        public static PatientView of(Patient p) { return of(p,null); }
        public static PatientView of(Patient p,PlanView course) { return new PatientView(p.id,p.account.fullName,p.birthDate,
            p.rehabilitationReason,p.notes,p.specialist==null?null:p.specialist.id,p.specialist==null?null:p.specialist.fullName,course); }
    }
    public record ExerciseView(Long id,String title,String instructions,Exercise.Unit unit) {
        public static ExerciseView of(Exercise e) { return new ExerciseView(e.id,e.title,e.instructions,e.unit); }
    }
    public record PlanView(Long id,Long patientId,String title,String goal,LocalDate startsOn,LocalDate endsOn,
        RehabPlan.Status status,LocalDate closedOn,long version,boolean finished,String review) {
        public static PlanView of(RehabPlan p) { return new PlanView(p.id,p.patient.id,p.title,p.goal,p.startsOn,p.endsOn,p.status,p.closedOn,p.version,p.finished(),p.review); }
    }
    public record AssignmentView(Long id,String title,String instructions,Exercise.Unit unit,double targetAmount) {
        public static AssignmentView of(Assignment a) { return new AssignmentView(a.id,a.title,a.instructions,a.unit,a.targetAmount); }
    }
    public record EntryView(Long id,Long assignmentId,String exerciseTitle,Exercise.Unit unit,LocalDate performedOn,
        DiaryEntry.Status status,double actualAmount,double targetAmount,int pain,int wellbeing,
        String comment,String feedback,OffsetDateTime updatedAt,long version) {
        public static EntryView of(DiaryEntry e) { Assignment a=e.assignment; return new EntryView(e.id,a.id,a.title,a.unit,e.performedOn,
            e.status,e.actualAmount,a.targetAmount,e.pain,e.wellbeing,e.comment,e.feedback,e.updatedAt,e.version); }
    }
    public record PlanDetail(PlanView plan,List<AssignmentView> assignments,List<EntryView> entries) {}
}
