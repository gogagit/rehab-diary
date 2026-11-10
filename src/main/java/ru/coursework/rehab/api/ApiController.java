package ru.coursework.rehab.api;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.validation.Valid;
import ru.coursework.rehab.service.*;
import ru.coursework.rehab.api.Requests.*;
import ru.coursework.rehab.api.Views.*;
import java.util.*;
/** Транспортный слой. Проверки ролей и владельца выполняются внутри сервисной транзакции. */
@RestController @RequestMapping("/api")
public class ApiController {
    private final RehabService service;
    public ApiController(RehabService service) { this.service=service; }
    @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody RegistrationInput in) { service.register(in); }
    @PostMapping("/specialists") @ResponseStatus(HttpStatus.CREATED)
    public UserView createSpecialist(@Valid @RequestBody SpecialistInput in) { return service.createSpecialist(in); }
    @PatchMapping("/patients/{id}/specialist")
    public PatientView assignSpecialist(@PathVariable Long id,@RequestBody SpecialistAssignment in) { return service.assignSpecialist(id,in.specialistId()); }
    @DeleteMapping("/patients/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePatient(@PathVariable Long id) { service.deletePatient(id); }
    @DeleteMapping("/specialists/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSpecialist(@PathVariable Long id) { service.deleteSpecialist(id); }
    @GetMapping("/auth/me") public Map<String,Object> me() { return Map.of("user",service.me(),"today",service.today()); }
    @GetMapping("/patients") public List<PatientView> patients() { return service.patients(); }
    @PostMapping("/patients") @ResponseStatus(HttpStatus.CREATED) public PatientView patient(@Valid @RequestBody PatientInput in) { return service.createPatient(in); }
    @GetMapping("/specialists") public List<UserView> specialists() { return service.specialists(); }
    @GetMapping("/exercises") public List<ExerciseView> exercises() { return service.exercises(); }
    @PostMapping("/exercises") @ResponseStatus(HttpStatus.CREATED) public ExerciseView exercise(@Valid @RequestBody ExerciseInput in) { return service.createExercise(in); }
    @GetMapping("/patients/{id}/plans") public List<PlanView> plans(@PathVariable Long id) { return service.plans(id); }
    @PostMapping("/patients/{id}/plans") @ResponseStatus(HttpStatus.CREATED) public PlanView plan(@PathVariable Long id,@Valid @RequestBody PlanInput in) { return service.createPlan(id,in); }
    @PutMapping("/plans/{id}/review")
    public PlanView review(@PathVariable Long id,@Valid @RequestBody ReviewInput in) { return service.review(id,in); }
    @GetMapping("/plans/{id}") public PlanDetail detail(@PathVariable Long id) { return service.detail(id); }
    @PostMapping("/plans/{id}/assignments") @ResponseStatus(HttpStatus.CREATED) public AssignmentView assignment(@PathVariable Long id,@Valid @RequestBody AssignmentInput in) { return service.addAssignment(id,in); }
    @DeleteMapping("/assignments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@PathVariable Long id) { service.removeAssignment(id); }
    @PatchMapping("/plans/{id}/status") public PlanView status(@PathVariable Long id,@Valid @RequestBody StatusInput in) { return service.changeStatus(id,in); }
    @PostMapping("/entries") @ResponseStatus(HttpStatus.CREATED) public EntryView entry(@Valid @RequestBody EntryInput in) { return service.writeEntry(null,in); }
    @PutMapping("/entries/{id}") public EntryView edit(@PathVariable Long id,@Valid @RequestBody EntryInput in) { return service.writeEntry(id,in); }
    @PutMapping("/entries/{id}/feedback") public EntryView feedback(@PathVariable Long id,@Valid @RequestBody FeedbackInput in) { return service.feedback(id,in); }
    @GetMapping("/plans/{id}/progress") public ProgressCalculator.Result progress(@PathVariable Long id) { return service.progress(id); }
    @GetMapping(value="/plans/{id}/export",produces="text/csv;charset=UTF-8") public ResponseEntity<String> csv(@PathVariable Long id) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=rehab-plan-"+id+".csv").body(service.csv(id));
    }
    @GetMapping("/audit") public Object audit(@RequestParam(defaultValue="0") int page) { return service.audit(page); }
}
