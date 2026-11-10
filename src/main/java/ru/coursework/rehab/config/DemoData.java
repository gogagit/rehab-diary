package ru.coursework.rehab.config;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.coursework.rehab.domain.*;
import ru.coursework.rehab.repository.*;
import java.time.*;
@Component
public class DemoData implements CommandLineRunner {
    private final AccountRepository accounts; private final PatientRepository patients; private final ExerciseRepository exercises;
    private final PlanRepository plans; private final AssignmentRepository assignments; private final EntryRepository entries;
    private final PasswordEncoder encoder; private final Clock clock;
    @Value("${app.demo-data}") private boolean demo;
    @Value("${app.admin-password}") private String adminPassword;
    @Value("${app.specialist-password}") private String specialistPassword;
    @Value("${app.patient-password}") private String patientPassword;
    public DemoData(AccountRepository accounts,PatientRepository patients,ExerciseRepository exercises,PlanRepository plans,
        AssignmentRepository assignments,EntryRepository entries,PasswordEncoder encoder,Clock clock) {
        this.accounts=accounts;this.patients=patients;this.exercises=exercises;this.plans=plans;this.assignments=assignments;this.entries=entries;this.encoder=encoder;this.clock=clock;
    }
    @Override @Transactional public void run(String... args) {
        if(accounts.count()==0) seedBase();
        if(demo) accounts.findByUsername("doctor").filter(a->a.deletedAt==null).ifPresent(this::seedFinishedCourses);
    }
    private void seedBase() {
        account("admin","Администратор",Account.Role.ADMIN,adminPassword);
        Account doctor=account("doctor","Анна Сергеевна Миронова",Account.Role.SPECIALIST,specialistPassword);
        account("doctor2","Павел Андреевич Соколов",Account.Role.SPECIALIST,specialistPassword);
        exercise("Ходьба в комфортном темпе","Демонстрационное назначение. Реальные условия занятия определяет специалист.",Exercise.Unit.MINUTES);
        exercise("Сгибание и разгибание кисти","Демонстрационный пример для дневника. Выполнять только по индивидуальному назначению.",Exercise.Unit.REPETITIONS);
        exercise("Дистанция ходьбы","Запишите фактически пройденное расстояние по назначенной программе.",Exercise.Unit.METERS);
        if(!demo) return;
        Patient p=patient("patient","Алексей Воронов",doctor,"Восстановление после травмы",LocalDate.of(1994,5,14));
        patient("patient2","Мария Белова",doctor,"Реабилитация после инсульта",LocalDate.of(1967,9,22));
        RehabPlan plan=new RehabPlan();plan.patient=p;plan.title="Возвращение к повседневной активности";
        plan.goal="Регулярно вести дневник и отслеживать переносимость согласованной нагрузки";
        LocalDate today=LocalDate.now(clock);plan.startsOn=today.minusDays(6);plan.endsOn=today.plusDays(21);plan.status=RehabPlan.Status.ACTIVE;plans.save(plan);
        int index=0;
        for(Exercise ex:exercises.findAllByOrderByTitle()) {
            if(ex.unit==Exercise.Unit.METERS) continue;
            Assignment a=new Assignment();a.plan=plan;a.exercise=ex;a.title=ex.title;a.instructions=ex.instructions;a.unit=ex.unit;a.targetAmount=ex.unit==Exercise.Unit.MINUTES?20:15;assignments.save(a);
            for(int day=0;day<6;day++) {
                if(day==2&&index==0) continue;
                DiaryEntry e=new DiaryEntry();e.assignment=a;e.performedOn=plan.startsOn.plusDays(day);
                e.status=day==3&&index==1?DiaryEntry.Status.SKIPPED:DiaryEntry.Status.DONE;
                e.actualAmount=e.status==DiaryEntry.Status.DONE?Math.min(a.targetAmount,8+day*2):0;
                e.pain=Math.max(1,5-day/2);e.wellbeing=Math.min(9,5+day/2);
                e.comment=e.status==DiaryEntry.Status.SKIPPED?"Не получилось выделить время":"Учебная запись о занятии";
                if(day==4)e.feedback="Спасибо за запись. Обсудим самочувствие на следующей встрече.";
                e.updatedAt=OffsetDateTime.now(clock).minusDays(6-day);entries.save(e);
            }index++;
        }
    }
    private void seedFinishedCourses(Account doctor) {
        finishedCourse("demo-recovery-alina","Крылова Алина Олеговна",doctor,LocalDate.of(1988,3,18),
            "Восстановление после травмы","Возвращение к привычным прогулкам",
            "В конце курса снова смогла гулять по своему обычному маршруту. Дневник помогал замечать, что получается лучше.");
        finishedCourse("demo-recovery-maksim","Орлов Максим Игоревич",doctor,LocalDate.of(1972,8,7),
            "Реабилитация после инсульта","Повседневные дела дома",
            "К концу курса стало проще справляться с бытовыми задачами. Записи помогали обсуждать со специалистом, где ещё нужна поддержка.");
    }
    private void finishedCourse(String login,String name,Account doctor,LocalDate birth,String reason,String title,String review) {
        if(accounts.existsByUsername(login)) return;
        Patient p=patient(login,name,doctor,reason,birth);
        LocalDate end=LocalDate.now(clock).minusDays(8);
        RehabPlan plan=new RehabPlan(); plan.patient=p; plan.title=title; plan.goal="Выполнить согласованный план занятий";
        plan.startsOn=end.minusDays(20); plan.endsOn=end; plan.closedOn=end;
        plan.status=RehabPlan.Status.COMPLETED; plan.review=review; plans.save(plan);
        exercises.findAllByOrderByTitle().stream().filter(e->e.unit==Exercise.Unit.MINUTES).findFirst().ifPresent(e->{
            Assignment a=new Assignment(); a.plan=plan; a.exercise=e; a.title=e.title;
            a.instructions=e.instructions; a.unit=e.unit; a.targetAmount=15; assignments.save(a);
            for(int day=0;day<=20;day++){
                DiaryEntry entry=new DiaryEntry(); entry.assignment=a; entry.performedOn=plan.startsOn.plusDays(day);
                entry.status=DiaryEntry.Status.DONE; entry.actualAmount=Math.min(15,8+day);
                entry.pain=Math.max(1,4-day/6); entry.wellbeing=Math.min(9,6+day/5);
                entry.comment="Учебная запись о занятии"; entry.updatedAt=OffsetDateTime.now(clock).minusDays(28-day);
                entries.save(entry);
            }
        });
    }
    private Account account(String login,String name,Account.Role role,String password) {
        Account a=new Account();a.username=login;a.fullName=name;a.role=role;a.passwordHash=encoder.encode(password);return accounts.save(a);
    }
    private Patient patient(String login,String name,Account specialist,String reason,LocalDate birthDate) {
        Patient p=new Patient();p.account=account(login,name,Account.Role.PATIENT,patientPassword);p.specialist=specialist;p.rehabilitationReason=reason;p.birthDate=birthDate;p.notes="Вымышленный пациент для демонстрации";return patients.save(p);
    }
    private void exercise(String title,String instructions,Exercise.Unit unit) {
        Exercise e=new Exercise();e.title=title;e.instructions=instructions;e.unit=unit;exercises.save(e);
    }
}
