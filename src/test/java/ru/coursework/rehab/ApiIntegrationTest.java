package ru.coursework.rehab;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import ru.coursework.rehab.domain.*;
import ru.coursework.rehab.repository.*;
import java.time.*;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:api;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc @Transactional
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PatientRepository patients;
    @Autowired PlanRepository plans;
    @Autowired AssignmentRepository assignments;
    @Autowired EntryRepository entries;
    @Autowired AccountRepository accounts;
    private Long patientId() { return patients.findByAccountUsername("patient").orElseThrow().id; }
    private RehabPlan active() { return plans.findByPatientIdOrderByIdDesc(patientId()).stream().filter(p->p.status==RehabPlan.Status.ACTIVE).findFirst().orElseThrow(); }
    private Assignment assignment() { return assignments.findByPlanIdOrderById(active().id).get(0); }
    private Map<String,Object> entry() {
        return new LinkedHashMap<>(Map.of("assignmentId",assignment().id,"performedOn",LocalDate.now().toString(),
            "status","DONE","actualAmount",12,"pain",2,"wellbeing",7,"comment","Занятие выполнено"));
    }
    private MockHttpServletRequestBuilder postJson(String path,Object body) throws Exception {
        return post(path).with(csrf()).contentType("application/json").content(json.writeValueAsString(body));
    }
    private MockHttpServletRequestBuilder putJson(String path,Object body) throws Exception {
        return put(path).with(csrf()).contentType("application/json").content(json.writeValueAsString(body));
    }
    @Test void unauthenticatedCannotRead() throws Exception { mvc.perform(get("/api/patients")).andExpect(status().isUnauthorized()); }
    @Test void publicPageAndTokenAvailable() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }
    @Test void realPasswordLoginWorks() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username","admin").param("password","Admin2026!"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true));
    }
    @Test void wrongPasswordFails() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username","admin").param("password","wrong"))
            .andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser("patient") void patientSeesOnlyOwnCard() throws Exception {
        mvc.perform(get("/api/patients")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(patientId()));
    }
    @Test @WithMockUser("patient2") void patientCannotReadAnotherPlan() throws Exception {
        mvc.perform(get("/api/plans/"+active().id)).andExpect(status().isForbidden());
    }
    @Test @WithMockUser("doctor2") void unrelatedSpecialistCannotReadPlan() throws Exception {
        mvc.perform(get("/api/plans/"+active().id)).andExpect(status().isForbidden());
    }
    @Test @WithMockUser("doctor") void csrfRequired() throws Exception {
        mvc.perform(post("/api/exercises").contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser("patient") void patientCannotCreatePlan() throws Exception {
        mvc.perform(postJson("/api/patients/"+patientId()+"/plans",Map.of("title","План","goal","Цель",
            "startsOn",LocalDate.now().toString(),"endsOn",LocalDate.now().plusDays(5).toString()))).andExpect(status().isForbidden());
    }
    @Test @WithMockUser("patient") void createAndDuplicateEntry() throws Exception {
        mvc.perform(postJson("/api/entries",entry())).andExpect(status().isCreated()).andExpect(jsonPath("$.actualAmount").value(12));
        mvc.perform(postJson("/api/entries",entry())).andExpect(status().isConflict());
    }
    @Test @WithMockUser("patient") void futureDateRejected() throws Exception {
        var body=entry();body.put("performedOn",LocalDate.now().plusDays(1).toString());
        mvc.perform(postJson("/api/entries",body)).andExpect(status().isBadRequest());
    }
    @Test @WithMockUser("patient") void dateBeforePlanRejected() throws Exception {
        var body=entry();body.put("performedOn",active().startsOn.minusDays(1).toString());
        mvc.perform(postJson("/api/entries",body)).andExpect(status().isBadRequest());
    }
    @Test @WithMockUser("patient") void skipRequiresReason() throws Exception {
        var body=entry();body.put("status","SKIPPED");body.put("actualAmount",0);body.put("comment","");
        mvc.perform(postJson("/api/entries",body)).andExpect(status().isBadRequest());
    }
    @Test @WithMockUser("patient") void skipRequiresZeroLoad() throws Exception {
        var body=entry();body.put("status","SKIPPED");
        mvc.perform(postJson("/api/entries",body)).andExpect(status().isBadRequest());
    }
    @Test @WithMockUser("patient") void skipCanBeRecorded() throws Exception {
        var body=entry();body.put("status","SKIPPED");body.put("actualAmount",0);
        mvc.perform(postJson("/api/entries",body)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SKIPPED"));
    }
    @Test @WithMockUser("patient") void doneRequiresPositiveLoad() throws Exception {
        var body=entry();body.put("actualAmount",0);
        mvc.perform(postJson("/api/entries",body)).andExpect(status().isBadRequest());
    }
    @Test @WithMockUser("patient") void invalidPainRejected() throws Exception {
        var body=entry();body.put("pain",11);
        mvc.perform(postJson("/api/entries",body)).andExpect(status().isBadRequest());
    }
    @Test @WithMockUser("doctor") void specialistCannotWritePatientDiary() throws Exception {
        mvc.perform(postJson("/api/entries",entry())).andExpect(status().isForbidden());
    }
    @Test @WithMockUser("patient") void staleVersionRejectedAndFeedbackResetOnEdit() throws Exception {
        JsonNode created=json.readTree(mvc.perform(postJson("/api/entries",entry())).andReturn().getResponse().getContentAsString());
        Long id=created.get("id").asLong();DiaryEntry e=entries.findById(id).orElseThrow();e.feedback="Прежний ответ";entries.flush();
        var body=entry();body.put("version",e.version);body.put("actualAmount",13);
        mvc.perform(putJson("/api/entries/"+id,body)).andExpect(status().isOk()).andExpect(jsonPath("$.feedback").doesNotExist());
        mvc.perform(putJson("/api/entries/"+id,body)).andExpect(status().isConflict());
    }
    @Test @WithMockUser("doctor") void specialistCanCommentAndPatientCannot() throws Exception {
        DiaryEntry e=entries.findByAssignmentPlanIdOrderByPerformedOnDescIdDesc(active().id).get(0);
        mvc.perform(putJson("/api/entries/"+e.id+"/feedback",Map.of("text","Спасибо за запись","version",e.version)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.feedback").value("Спасибо за запись"));
        mvc.perform(putJson("/api/entries/"+e.id+"/feedback",Map.of("text","Чужой ответ","version",e.version))
            .with(user("patient"))).andExpect(status().isForbidden());
    }
    @Test @WithMockUser("doctor") void activeAssignmentsImmutable() throws Exception {
        mvc.perform(postJson("/api/plans/"+active().id+"/assignments",Map.of("exerciseId",assignment().exercise.id,"targetAmount",10)))
            .andExpect(status().isConflict());
    }
    @Test @WithMockUser("doctor") void fullPlanLifecycle() throws Exception {
        Long second=patients.findByAccountUsername("patient2").orElseThrow().id;
        JsonNode created=json.readTree(mvc.perform(postJson("/api/patients/"+second+"/plans",Map.of("title","Новый план","goal","Вести дневник",
            "startsOn",LocalDate.now().toString(),"endsOn",LocalDate.now().plusDays(7).toString()))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id=created.get("id").asLong();
        mvc.perform(patch("/api/plans/"+id+"/status").with(csrf()).contentType("application/json").content("{\"status\":\"ACTIVE\",\"version\":0}")).andExpect(status().isConflict());
        mvc.perform(postJson("/api/plans/"+id+"/assignments",Map.of("exerciseId",assignment().exercise.id,"targetAmount",10))).andExpect(status().isCreated());
        mvc.perform(patch("/api/plans/"+id+"/status").with(csrf()).contentType("application/json").content("{\"status\":\"ACTIVE\",\"version\":0}")).andExpect(status().isOk());
        mvc.perform(patch("/api/plans/"+id+"/status").with(csrf()).contentType("application/json").content("{\"status\":\"COMPLETED\",\"version\":1}")).andExpect(status().isOk());
        mvc.perform(patch("/api/plans/"+id+"/status").with(csrf()).contentType("application/json").content("{\"status\":\"ARCHIVED\",\"version\":2}")).andExpect(status().isOk());
        mvc.perform(get("/api/plans/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.assignments.length()").value(1));
    }
    @Test @WithMockUser("patient") void closedPlanCannotBeEdited() throws Exception {
        var body=entry();RehabPlan p=active();p.status=RehabPlan.Status.COMPLETED;plans.flush();
        mvc.perform(postJson("/api/entries",body)).andExpect(status().isConflict());
    }
    @Test @WithMockUser("doctor") void cannotActivateSecondPlan() throws Exception {
        RehabPlan a=active();RehabPlan p=new RehabPlan();p.patient=a.patient;p.title="Второй";p.goal="Цель";p.startsOn=a.startsOn;p.endsOn=a.endsOn;plans.saveAndFlush(p);
        Assignment item=new Assignment();Assignment old=assignment();item.plan=p;item.exercise=old.exercise;item.title=old.title;item.instructions=old.instructions;item.unit=old.unit;item.targetAmount=10;assignments.saveAndFlush(item);
        mvc.perform(patch("/api/plans/"+p.id+"/status").with(csrf()).contentType("application/json").content("{\"status\":\"ACTIVE\",\"version\":0}")).andExpect(status().isConflict());
    }
    @Test @WithMockUser("patient") void csvEscapesFormula() throws Exception {
        var body=entry();body.put("comment","=HYPERLINK(\"x\")");
        mvc.perform(postJson("/api/entries",body)).andExpect(status().isCreated());
        String csv=mvc.perform(get("/api/plans/"+active().id+"/export")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("'=HYPERLINK").contains("Упражнение");
    }
    @Test @WithMockUser("patient") void auditRestricted() throws Exception { mvc.perform(get("/api/audit")).andExpect(status().isForbidden()); }
    @Test @WithMockUser("admin") void adminReadsAudit() throws Exception { mvc.perform(get("/api/audit")).andExpect(status().isOk()); }
    @Test @WithMockUser("doctor") void patientCreationAndDuplicateLogin() throws Exception {
        Long doc=accounts.findByUsername("doctor").orElseThrow().id;
        var body=Map.of("username","newpatient@example.test","fullName","Тестовый пациент",
            "birthDate","1990-01-01","rehabilitationReason","Учебная запись","notes","","specialistId",doc);
        mvc.perform(postJson("/api/patients",body)).andExpect(status().isCreated());
        mvc.perform(postJson("/api/patients",body)).andExpect(status().isConflict());
    }
    @Test @WithMockUser("doctor") void archivedDraftHasZeroExpectedSessions() throws Exception {
        Long id=json.readTree(mvc.perform(postJson("/api/patients/"+patientId()+"/plans",Map.of("title","Черновик","goal","Цель",
            "startsOn",LocalDate.now().minusDays(3).toString(),"endsOn",LocalDate.now().plusDays(5).toString())))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(postJson("/api/plans/"+id+"/assignments",Map.of("exerciseId",assignment().exercise.id,"targetAmount",10))).andExpect(status().isCreated());
        mvc.perform(patch("/api/plans/"+id+"/status").with(csrf()).contentType("application/json").content("{\"status\":\"ARCHIVED\",\"version\":0}")).andExpect(status().isOk());
        mvc.perform(get("/api/plans/"+id+"/progress")).andExpect(status().isOk()).andExpect(jsonPath("$.scheduled").value(0));
    }
    @Test @WithMockUser("doctor") void bcryptByteLimitValidated() throws Exception {
        mvc.perform(postJson("/api/patients",Map.of("username","unicodepatient@example.test","password","я".repeat(40),"fullName","Пациент",
            "birthDate","1990-01-01","rehabilitationReason","Тест","notes","","specialistId",accounts.findByUsername("doctor").orElseThrow().id)))
            .andExpect(status().isBadRequest());
    }

    private Map<String,String> credentials(String login) {
        return Map.of("username",login,"fullName","Тестовая учётная запись",
            "password","SecurePass2026!","passwordConfirmation","SecurePass2026!");
    }
    @Test void registrationDoesNotCreateAnAuthenticatedSession() throws Exception {
        var body=new HashMap<>(Map.of("username","invalid-address","fullName","Тестовый пациент","birthDate","1990-01-01"));
        mvc.perform(postJson("/api/auth/register",body)).andExpect(status().isBadRequest());
        body.put("username","NewPublicPatient@example.test");
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isForbidden());
        var registered=mvc.perform(postJson("/api/auth/register",body)).andExpect(status().isNoContent()).andReturn();
        assertThat(registered.getRequest().getSession(false)).isNull();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/register",body)).andExpect(status().isConflict());
    }

    @Test void trustedDeviceIsSingleUseAndCanBeRevoked() throws Exception {
        String patient="/api/patients/"+patientId();
        mvc.perform(postJson(patient+"/device-invite",Map.of()).with(user("doctor")))
            .andExpect(status().isForbidden());
        var issued=mvc.perform(postJson(patient+"/device-invite",Map.of()).with(user("admin")))
            .andExpect(status().isOk()).andReturn();
        String token=json.readTree(issued.getResponse().getContentAsString()).get("token").asText();
        var paired=mvc.perform(postJson("/api/auth/device",Map.of("token",token)))
            .andExpect(status().isNoContent()).andExpect(cookie().httpOnly("rehab_device",true)).andReturn();
        var device=paired.getResponse().getCookie("rehab_device");
        mvc.perform(postJson("/api/auth/device",Map.of("token",token))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username","patient").param("password","Patient2026!"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(device)).andExpect(status().isOk())
            .andExpect(jsonPath("$.user.role").value("PATIENT"));
        mvc.perform(get("/api/patients").cookie(device)).andExpect(jsonPath("$.length()").value(1));
        Long otherPatient=patients.findByAccountUsername("patient2").orElseThrow().id;
        String secondInvite=issueDeviceInvite(patient);
        var second=mvc.perform(postJson("/api/auth/device",Map.of("token",secondInvite,"name","Мой телефон")))
            .andExpect(status().isNoContent()).andReturn().getResponse().getCookie("rehab_device");
        var listed=mvc.perform(get(patient+"/devices").with(user("admin"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.devices.length()").value(2))
            .andExpect(jsonPath("$.devices[0].name").value("Мой телефон")).andReturn();
        var list=json.readTree(listed.getResponse().getContentAsString()).get("devices");
        long firstId=list.get(1).get("id").asLong();
        mvc.perform(delete("/api/patients/"+otherPatient+"/devices/"+firstId).with(csrf()).with(user("admin")))
            .andExpect(status().isNotFound());
        mvc.perform(delete(patient+"/devices/"+firstId).with(csrf()).with(user("admin"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(device)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(second)).andExpect(status().isOk());
        String otherInvite=issueDeviceInvite("/api/patients/"+otherPatient);
        mvc.perform(postJson("/api/auth/device",Map.of("token",otherInvite)).cookie(second)).andExpect(status().isConflict());
        var pause=mvc.perform(post("/api/auth/logout").with(csrf()).cookie(second))
            .andExpect(status().isNoContent()).andExpect(cookie().httpOnly("rehab_device_paused",true))
            .andReturn().getResponse().getCookie("rehab_device_paused");
        mvc.perform(get("/api/auth/me").cookie(second,pause)).andExpect(status().isUnauthorized());
        mvc.perform(get(patient+"/devices").with(user("admin"))).andExpect(jsonPath("$.devices.length()").value(1));
        mvc.perform(post("/api/auth/device/resume").cookie(second,pause)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/device/resume").with(csrf()).cookie(second,pause))
            .andExpect(status().isNoContent()).andExpect(cookie().maxAge("rehab_device_paused",0));
        mvc.perform(get("/api/patients").cookie(second)).andExpect(jsonPath("$[0].id").value(patientId()));
        var switched=mvc.perform(postJson("/api/auth/device",Map.of("token",otherInvite)).cookie(second,pause))
            .andExpect(status().isNoContent()).andReturn().getResponse().getCookie("rehab_device");
        mvc.perform(get("/api/patients").cookie(switched)).andExpect(jsonPath("$[0].id").value(otherPatient));
        mvc.perform(post("/api/auth/device/resume").with(csrf()).cookie(second,pause)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").param("forget","true").with(csrf()).cookie(switched))
            .andExpect(status().isNoContent()).andExpect(cookie().maxAge("rehab_device",0));
        mvc.perform(post("/api/auth/device/resume").with(csrf()).cookie(switched)).andExpect(status().isUnauthorized());
    }

    private String issueDeviceInvite(String patientPath) throws Exception {
        var issued=mvc.perform(postJson(patientPath+"/device-invite",Map.of()).with(user("admin")))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(issued.getResponse().getContentAsString()).get("token").asText();
    }

    @Test @WithMockUser("doctor") void staffCannotUseAdminOperations() throws Exception {
        Long doctor=accounts.findByUsername("doctor").orElseThrow().id;
        mvc.perform(postJson("/api/specialists",credentials("restricted@example.test"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/patients/"+patientId()+"/specialist").with(csrf()).contentType("application/json")
            .content("{\"specialistId\":"+doctor+"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/patients/"+patientId()).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/specialists/"+doctor).with(csrf())).andExpect(status().isForbidden());
    }
    @Test @WithMockUser("admin") void specialistReassignmentAndDeletionPreserveDiary() throws Exception {
        Long patient=patientId(), plan=active().id;
        long specialist=json.readTree(mvc.perform(postJson("/api/specialists",credentials("newdoctor@example.test")))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(patch("/api/patients/"+patient+"/specialist").with(csrf()).contentType("application/json")
            .content("{\"specialistId\":"+specialist+"}")).andExpect(status().isOk());
        mvc.perform(get("/api/plans/"+plan).with(user("doctor"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/plans/"+plan).with(user("newdoctor@example.test"))).andExpect(status().isOk());
        mvc.perform(delete("/api/specialists/"+specialist).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/plans/"+plan).with(user("newdoctor@example.test"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/plans/"+plan).with(user("patient"))).andExpect(status().isOk());
        mvc.perform(get("/api/specialists")).andExpect(jsonPath("$[?(@.username == 'newdoctor@example.test')]").isEmpty());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username","newdoctor@example.test").param("password","SecurePass2026!"))
            .andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser("admin") void patientDeletionRemovesDependentRecordsAndBlocksAccess() throws Exception {
        Long patient=patientId(),plan=active().id,assignment=assignment().id;
        mvc.perform(delete("/api/patients/"+patient).with(csrf())).andExpect(status().isNoContent());
        assertThat(patients.findById(patient)).isEmpty();
        assertThat(plans.findById(plan)).isEmpty();
        assertThat(assignments.findById(assignment)).isEmpty();
        assertThat(entries.findByAssignmentPlanIdOrderByPerformedOnDescIdDesc(plan)).isEmpty();
        mvc.perform(get("/api/auth/me").with(user("patient"))).andExpect(status().isForbidden());
    }

    @Test @WithMockUser("patient") void clinicalAndSupportChatAreSeparateAndReadByRecipient() throws Exception {
        String base="/api/chat/"+patientId()+"/";
        var sent=mvc.perform(postJson(base+"DOCTOR",Map.of("text","  Вопрос специалисту  ")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.body").value("Вопрос специалисту"))
            .andReturn();
        long id=json.readTree(sent.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(postJson(base+"SUPPORT",Map.of("text","Вопрос о входе"))).andExpect(status().isCreated());
        mvc.perform(get(base+"DOCTOR").with(user("admin"))).andExpect(status().isForbidden());
        mvc.perform(get(base+"DOCTOR").with(user("doctor2"))).andExpect(status().isForbidden());
        mvc.perform(get(base+"DOCTOR").with(user("patient2"))).andExpect(status().isForbidden());
        mvc.perform(get(base+"SUPPORT").with(user("doctor"))).andExpect(status().isForbidden());
        mvc.perform(get(base+"SUPPORT").with(user("admin"))).andExpect(jsonPath("$.messages[0].body").value("Вопрос о входе"));
        mvc.perform(get("/api/chat/threads").with(user("doctor"))).andExpect(jsonPath("$[0].unread").value(1));
        String ids=json.writeValueAsString(Map.of("ids",List.of(id)));
        mvc.perform(patch(base+"DOCTOR/read").with(csrf()).contentType("application/json").content(ids))
            .andExpect(status().isNoContent());
        mvc.perform(get(base+"DOCTOR").with(user("doctor"))).andExpect(jsonPath("$.messages[0].unread").value(true));
        mvc.perform(patch(base+"DOCTOR/read").with(user("doctor")).with(csrf()).contentType("application/json").content(ids))
            .andExpect(status().isNoContent());
        mvc.perform(get(base+"DOCTOR").with(user("doctor"))).andExpect(jsonPath("$.messages[0].unread").value(false));
        mvc.perform(postJson(base+"DOCTOR",Map.of("text","Ответ")).with(user("doctor"))).andExpect(status().isCreated());
        mvc.perform(get("/api/chat/threads")).andExpect(jsonPath("$[0].unread").value(1));
        long next=accounts.findByUsername("doctor2").orElseThrow().id;
        mvc.perform(patch("/api/patients/"+patientId()+"/specialist").with(user("admin")).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("specialistId",next)))).andExpect(status().isOk());
        mvc.perform(get(base+"DOCTOR").with(user("doctor"))).andExpect(status().isForbidden());
        mvc.perform(get(base+"DOCTOR").with(user("doctor2"))).andExpect(jsonPath("$.messages").isEmpty());
        mvc.perform(get(base+"DOCTOR")).andExpect(jsonPath("$.messages").isEmpty());
        mvc.perform(delete("/api/patients/"+patientId()).with(user("admin")).with(csrf())).andExpect(status().isNoContent());
    }
    @Test @WithMockUser("patient") void onlyOwnerCanReviewFinishedCourse() throws Exception {
        var completed=patients.findByAccountUsername("demo-recovery-alina").orElseThrow();
        var plan=plans.findByPatientIdOrderByIdDesc(completed.id).get(0);
        var review=Map.of("text","Курс помог вернуться к привычным делам");
        mvc.perform(putJson("/api/plans/"+active().id+"/review",review)).andExpect(status().isConflict());
        mvc.perform(putJson("/api/plans/"+plan.id+"/review",review)).andExpect(status().isForbidden());
        mvc.perform(putJson("/api/plans/"+plan.id+"/review",review).with(user("doctor"))).andExpect(status().isForbidden());
        mvc.perform(putJson("/api/plans/"+plan.id+"/review",review).with(user("demo-recovery-alina")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.review").value(review.get("text")));
        mvc.perform(get("/api/patients").with(user("demo-recovery-alina")))
            .andExpect(jsonPath("$[0].finishedCourse.review").value(review.get("text")));
    }
}
