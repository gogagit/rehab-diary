package ru.coursework.rehab.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.coursework.rehab.domain.*;
import ru.coursework.rehab.repository.*;
import ru.coursework.rehab.api.Requests.*;
import ru.coursework.rehab.api.Views.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import static ru.coursework.rehab.service.BusinessException.*;

/** Единая граница проверки принадлежности данных и транзакционных бизнес-операций. */
@Service @Transactional(readOnly=true)
public class RehabService {
    private final AccountRepository accounts;
    private final PatientRepository patients;
    private final ExerciseRepository exercises;
    private final PlanRepository plans;
    private final AssignmentRepository assignments;
    private final EntryRepository entries;
    private final AuditRepository audits;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final EntityManager em;
    public RehabService(AccountRepository accounts,PatientRepository patients,ExerciseRepository exercises,
        PlanRepository plans,AssignmentRepository assignments,EntryRepository entries,AuditRepository audits,
        PasswordEncoder encoder,Clock clock,EntityManager em) {
        this.accounts=accounts; this.patients=patients; this.exercises=exercises; this.plans=plans;
        this.assignments=assignments; this.entries=entries; this.audits=audits; this.encoder=encoder; this.clock=clock; this.em=em;
    }
    public LocalDate today() { return LocalDate.now(clock); }
    Account actor() {
        String login=SecurityContextHolder.getContext().getAuthentication().getName();
        return accounts.findByUsername(login).filter(a->a.deletedAt==null).orElseThrow(()->new AccessDeniedException("Пользователь недоступен"));
    }
    public UserView me() { return UserView.of(actor()); }
    private boolean staff(Account a) { return a.role==Account.Role.SPECIALIST || a.role==Account.Role.ADMIN; }
    private void requireStaff() { if(!staff(actor())) throw new AccessDeniedException("Действие доступно специалисту"); }
    private void requireAdmin() {
        if(actor().role!=Account.Role.ADMIN) throw new AccessDeniedException("Действие доступно администратору");
    }
    private void access(Patient p) {
        Account a=actor();
        if(a.role==Account.Role.ADMIN || p.account.id.equals(a.id) || (p.specialist!=null && p.specialist.id.equals(a.id))) return;
        throw new AccessDeniedException("Нет доступа к этому пациенту");
    }
    private Patient patient(Long id) { Patient p=patients.findById(id).orElseThrow(BusinessException::missing); access(p); return p; }
    private RehabPlan plan(Long id) { RehabPlan p=plans.findById(id).orElseThrow(BusinessException::missing); access(p.patient); return p; }
    private void lockPatient(Patient p) { patients.findLockedById(p.id).orElseThrow(BusinessException::missing); }
    public List<PatientView> patients() {
        Account a=actor();
        List<Patient> result=switch(a.role) {
            case ADMIN -> patients.findAll();
            case SPECIALIST -> patients.findBySpecialistUsernameOrderByIdDesc(a.username);
            case PATIENT -> patients.findByAccountUsername(a.username).stream().toList();
        };
        Map<Long,PlanView> courses=new HashMap<>();
        if(!result.isEmpty()) for(RehabPlan p:plans.findFinishedByPatientIds(result.stream().map(x->x.id).toList()))
            courses.putIfAbsent(p.patient.id,PlanView.of(p));
        return result.stream().map(p->PatientView.of(p,courses.get(p.id))).toList();
    }
    public List<UserView> specialists() { requireStaff(); return accounts.findByRole(Account.Role.SPECIALIST).stream().filter(a->a.deletedAt==null).map(UserView::of).toList(); }
    @Transactional public PatientView createPatient(PatientInput in) {
        requireStaff();
        Account owner=lockedSpecialist(in.specialistId());
        Account actor=actor();
        if(actor.role!=Account.Role.ADMIN&&!owner.id.equals(actor.id)) throw new AccessDeniedException("Можно закрепить пациента только за собой");
        Account a=createAccount(in.username(),in.fullName(),in.password(),Account.Role.PATIENT);
        Patient p=new Patient(); p.account=a; p.specialist=owner; p.birthDate=in.birthDate();
        p.rehabilitationReason=in.rehabilitationReason().trim(); p.notes=in.notes(); patients.saveAndFlush(p);
        audit("CREATE","PATIENT",p.id); return PatientView.of(p);
    }
    private Account lockedSpecialist(Long id) {
        return accounts.findLockedById(id).filter(a->a.role==Account.Role.SPECIALIST && a.deletedAt==null)
            .orElseThrow(BusinessException::missing);
    }
    private void checkPassword(String password,String confirmation) {
        if(!password.equals(confirmation)) throw invalid("Пароли не совпадают");
        if(!password.codePoints().anyMatch(Character::isLowerCase) ||
            !password.codePoints().anyMatch(Character::isUpperCase) ||
            !password.codePoints().anyMatch(Character::isDigit) ||
            password.codePoints().noneMatch(c->!Character.isLetterOrDigit(c) && !Character.isWhitespace(c) && !Character.isISOControl(c)))
            throw invalid("Пароль должен содержать строчную и заглавную буквы, цифру и специальный символ");
    }
    private Account createAccount(String username,String fullName,String password,Account.Role role) {
        String login=username.toLowerCase(Locale.ROOT);
        if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw invalid("Пароль должен занимать не более 72 байт в UTF-8");
        if(accounts.existsByUsername(login)) throw conflict("Логин уже занят");
        Account a=new Account(); a.username=login; a.fullName=fullName.trim(); a.role=role; a.passwordHash=encoder.encode(password);
        return accounts.saveAndFlush(a);
    }
    @Transactional public void register(RegistrationInput in) {
        checkPassword(in.password(),in.passwordConfirmation());
        Account a=createAccount(in.username(),in.fullName(),in.password(),Account.Role.PATIENT);
        Patient p=new Patient(); p.account=a; p.birthDate=in.birthDate();
        p.rehabilitationReason=in.rehabilitationReason()==null?"":in.rehabilitationReason().trim();
        patients.saveAndFlush(p);
        recordAudit(a.username,"REGISTER","PATIENT",p.id);
    }
    @Transactional public UserView createSpecialist(SpecialistInput in) {
        requireAdmin(); checkPassword(in.password(),in.passwordConfirmation());
        Account a=createAccount(in.username(),in.fullName(),in.password(),Account.Role.SPECIALIST);
        audit("CREATE","SPECIALIST",a.id); return UserView.of(a);
    }
    @Transactional public PatientView assignSpecialist(Long id,Long specialistId) {
        requireAdmin();
        Account specialist=specialistId==null?null:lockedSpecialist(specialistId);
        Patient p=patients.findLockedById(id).orElseThrow(BusinessException::missing);
        p.specialist=specialist;
        audit(specialist==null?"UNASSIGN":"ASSIGN","PATIENT",id);
        return PatientView.of(p);
    }
    @Transactional public void deleteSpecialist(Long id) {
        requireAdmin(); Account a=lockedSpecialist(id);
        em.createQuery("update Patient p set p.specialist = null where p.specialist.id = :id")
            .setParameter("id",id).executeUpdate();
        retire(a); audit("DELETE","SPECIALIST",id);
    }
    @Transactional public void deletePatient(Long id) {
        requireAdmin(); Patient p=patients.findLockedById(id).orElseThrow(BusinessException::missing);
        for (RehabPlan plan:plans.findByPatientIdOrderByIdDesc(id)) {
            entries.deleteAll(entries.findByAssignmentPlanIdOrderByPerformedOnDescIdDesc(plan.id));
            entries.flush();
            assignments.deleteAll(assignments.findByPlanIdOrderById(plan.id));
            assignments.flush();
            plans.delete(plan);
        }
        plans.flush();
        em.createQuery("select m from ChatMessage m where m.patient.id = :id",ChatMessage.class).setParameter("id",id).getResultList().forEach(em::remove);
        em.flush();
        patients.delete(p); patients.flush(); retire(p.account);
        audit("DELETE","PATIENT",id);
    }
    // Reserve the old login so an existing session cannot inherit a newly created account.
    private void retire(Account a) {
        a.deletedAt=OffsetDateTime.now(clock); a.fullName="Удалённая учётная запись"; a.passwordHash="";
    }
    public List<ExerciseView> exercises() { actor(); return exercises.findAllByOrderByTitle().stream().map(ExerciseView::of).toList(); }
    @Transactional public ExerciseView createExercise(ExerciseInput in) {
        requireStaff(); Exercise e=new Exercise(); e.title=in.title().trim(); e.instructions=in.instructions().trim(); e.unit=in.unit();
        exercises.saveAndFlush(e); audit("CREATE","EXERCISE",e.id); return ExerciseView.of(e);
    }
    public List<PlanView> plans(Long patientId) { patient(patientId); return plans.findByPatientIdOrderByIdDesc(patientId).stream().map(PlanView::of).toList(); }
    @Transactional public PlanView createPlan(Long patientId,PlanInput in) {
        requireStaff(); Patient patient=patient(patientId);
        if(in.endsOn().isBefore(in.startsOn()) || in.endsOn().isAfter(in.startsOn().plusDays(365))) throw invalid("Продолжительность плана — от 1 до 366 дней");
        RehabPlan p=new RehabPlan(); p.patient=patient; p.title=in.title().trim(); p.goal=in.goal().trim(); p.startsOn=in.startsOn(); p.endsOn=in.endsOn();
        plans.saveAndFlush(p); audit("CREATE","PLAN",p.id); return PlanView.of(p);
    }
    public PlanDetail detail(Long id) {
        RehabPlan p=plan(id); return new PlanDetail(PlanView.of(p),assignments.findByPlanIdOrderById(id).stream().map(AssignmentView::of).toList(),
            entries.findByAssignmentPlanIdOrderByPerformedOnDescIdDesc(id).stream().map(EntryView::of).toList());
    }
    @Transactional public AssignmentView addAssignment(Long planId,AssignmentInput in) {
        requireStaff(); RehabPlan p=plan(planId); lockPatient(p.patient); em.refresh(p);
        if(p.status!=RehabPlan.Status.DRAFT) throw conflict("Назначения меняются только в черновике. Для нового назначения создайте новый план");
        if(!Double.isFinite(in.targetAmount())) throw invalid("Нагрузка должна быть конечным числом");
        if(assignments.existsByPlanIdAndExerciseId(planId,in.exerciseId())) throw conflict("Упражнение уже назначено");
        Exercise e=exercises.findById(in.exerciseId()).orElseThrow(BusinessException::missing);
        Assignment a=new Assignment(); a.plan=p; a.exercise=e; a.title=e.title; a.instructions=e.instructions; a.unit=e.unit; a.targetAmount=in.targetAmount();
        assignments.saveAndFlush(a); audit("ADD_ASSIGNMENT","PLAN",p.id); return AssignmentView.of(a);
    }
    @Transactional public void removeAssignment(Long id) {
        requireStaff(); Assignment a=assignments.findById(id).orElseThrow(BusinessException::missing);
        access(a.plan.patient); lockPatient(a.plan.patient); em.refresh(a.plan);
        if(a.plan.status!=RehabPlan.Status.DRAFT) throw conflict("Удаление доступно только в черновике");
        assignments.delete(a); audit("REMOVE_ASSIGNMENT","PLAN",a.plan.id);
    }
    @Transactional public PlanView changeStatus(Long id,StatusInput in) {
        requireStaff(); RehabPlan p=plan(id); lockPatient(p.patient); em.refresh(p);
        if(in.version()!=p.version) throw conflict("План изменился. Обновите страницу");
        if(p.status==RehabPlan.Status.DRAFT&&in.status()==RehabPlan.Status.ACTIVE) {
            if(p.endsOn.isBefore(today())) throw conflict("Период плана уже закончился");
            if(assignments.findByPlanIdOrderById(id).isEmpty()) throw conflict("Добавьте хотя бы одно упражнение");
            if(plans.existsByPatientIdAndStatus(p.patient.id,RehabPlan.Status.ACTIVE)) throw conflict("У пациента уже есть активный план");
        } else if(p.status==RehabPlan.Status.ACTIVE&&in.status()==RehabPlan.Status.COMPLETED) {
            if(today().isBefore(p.startsOn)) throw conflict("План ещё не начался");
            p.closedOn=today().isBefore(p.endsOn)?today():p.endsOn;
        } else if((p.status==RehabPlan.Status.DRAFT||p.status==RehabPlan.Status.COMPLETED)&&in.status()==RehabPlan.Status.ARCHIVED) {
            if(p.status==RehabPlan.Status.DRAFT) p.closedOn=p.startsOn.minusDays(1);
            // Архив хранит назначения и записи; возврат к редактированию не предусмотрен.
        } else throw conflict("Недопустимый переход состояния плана");
        p.status=in.status(); plans.flush(); audit("STATUS_"+p.status,"PLAN",p.id); return PlanView.of(p);
    }
    @Transactional public PlanView review(Long id,ReviewInput in) {
        Account a=actor();
        if(a.role!=Account.Role.PATIENT) throw new AccessDeniedException("Отзыв может оставить только пациент");
        RehabPlan p=plan(id);
        lockPatient(p.patient); em.refresh(p);
        if(!p.finished()) throw conflict("Отзыв доступен после завершения курса");
        p.review=in.text().strip(); plans.flush(); audit("REVIEW","PLAN",id); return PlanView.of(p);
    }
    @Transactional public EntryView writeEntry(Long id,EntryInput in) {
        Assignment assignment=assignments.findById(in.assignmentId()).orElseThrow(BusinessException::missing);
        RehabPlan p=assignment.plan; access(p.patient);
        if(!p.patient.account.id.equals(actor().id)) throw new AccessDeniedException("Дневник заполняет сам пациент");
        lockPatient(p.patient); em.refresh(p);
        if(p.status!=RehabPlan.Status.ACTIVE) throw conflict("Дневник доступен для изменения только у активного плана");
        if(in.performedOn().isAfter(today())||in.performedOn().isBefore(p.startsOn)||in.performedOn().isAfter(p.endsOn))
            throw invalid("Дата должна быть в периоде плана и не позже сегодняшней");
        if(!Double.isFinite(in.actualAmount())) throw invalid("Нагрузка должна быть конечным числом");
        if(in.status()==DiaryEntry.Status.DONE&&in.actualAmount()<=0) throw invalid("Укажите положительный объём выполненного занятия");
        if(in.status()==DiaryEntry.Status.SKIPPED&&(in.actualAmount()!=0||in.comment().isBlank())) throw invalid("Для пропуска укажите нулевую нагрузку и причину");
        DiaryEntry e;
        if(id==null) {
            if(entries.existsByAssignmentIdAndPerformedOn(assignment.id,in.performedOn())) throw conflict("Запись на эту дату уже есть. Откройте её для изменения");
            e=new DiaryEntry(); e.assignment=assignment; e.performedOn=in.performedOn();
        } else {
            e=entries.findById(id).orElseThrow(BusinessException::missing); em.refresh(e);
            if(!e.assignment.id.equals(assignment.id)||!e.performedOn.equals(in.performedOn())) throw invalid("У записи нельзя менять назначение и дату");
            if(in.version()==null||in.version()!=e.version) throw conflict("Запись изменена. Обновите страницу");
            e.feedback=null; // Старый отзыв не относится к новой редакции фактических данных.
        }
        e.status=in.status(); e.actualAmount=in.actualAmount(); e.pain=in.pain(); e.wellbeing=in.wellbeing(); e.comment=in.comment().trim(); e.updatedAt=OffsetDateTime.now(clock);
        entries.saveAndFlush(e); audit(id==null?"CREATE":"EDIT","ENTRY",e.id); return EntryView.of(e);
    }
    @Transactional public EntryView feedback(Long id,FeedbackInput in) {
        requireStaff(); DiaryEntry e=entries.findById(id).orElseThrow(BusinessException::missing);
        access(e.assignment.plan.patient); lockPatient(e.assignment.plan.patient); em.refresh(e);
        if(in.version()!=e.version) throw conflict("Запись изменилась. Перечитайте её перед комментированием");
        e.feedback=in.text().trim(); entries.flush(); audit("FEEDBACK","ENTRY",e.id); return EntryView.of(e);
    }
    public ProgressCalculator.Result progress(Long id) {
        RehabPlan p=plan(id); return ProgressCalculator.calculate(p,assignments.findByPlanIdOrderById(id),entries.findByAssignmentPlanIdOrderByPerformedOnDescIdDesc(id),today());
    }
    public String csv(Long id) {
        plan(id); StringBuilder out=new StringBuilder("\ufeffДата;Упражнение;Статус;Факт;Цель;Единица;Боль;Самочувствие;Комментарий;Ответ специалиста\r\n");
        for(DiaryEntry e:entries.findByAssignmentPlanIdOrderByPerformedOnDescIdDesc(id)) {
            List<Object> row=Arrays.asList(e.performedOn,e.assignment.title,e.status,e.actualAmount,e.assignment.targetAmount,e.assignment.unit,e.pain,e.wellbeing,e.comment,e.feedback);
            out.append(row.stream().map(RehabService::csvCell).collect(java.util.stream.Collectors.joining(";"))).append("\r\n");
        } return out.toString();
    }
    private static String csvCell(Object value) {
        String s=value==null?"":value.toString();
        String stripped=s.stripLeading();
        if(!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0))>=0) s="'"+s;
        return "\""+s.replace("\"","\"\"")+"\"";
    }
    public org.springframework.data.domain.Page<AuditEvent> audit(int page) {
        if(actor().role!=Account.Role.ADMIN) throw new AccessDeniedException("Журнал доступен администратору");
        if(page<0) throw invalid("Номер страницы не может быть отрицательным");
        return audits.findAll(org.springframework.data.domain.PageRequest.of(page,30,org.springframework.data.domain.Sort.by("id").descending()));
    }
    private void audit(String action,String type,Long id) {
        recordAudit(actor().username,action,type,id);
    }
    private void recordAudit(String username,String action,String type,Long id) {
        AuditEvent e=new AuditEvent(); e.occurredAt=OffsetDateTime.now(clock); e.actor=username; e.action=action; e.entityType=type; e.entityId=id; audits.save(e);
    }
}

