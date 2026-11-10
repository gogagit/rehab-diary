package ru.coursework.rehab;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import ru.coursework.rehab.domain.*;
import ru.coursework.rehab.repository.*;
import ru.coursework.rehab.service.*;
import ru.coursework.rehab.api.Requests.StatusInput;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:concurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.jpa.hibernate.ddl-auto=create-drop"})
class ConcurrencyTest {
    @Autowired PlatformTransactionManager tx;
    @Autowired RehabService service;
    @Autowired PatientRepository patients;
    @Autowired PlanRepository plans;
    @Autowired ExerciseRepository exercises;
    @Autowired AssignmentRepository assignments;
    @Test void parallelActivationLeavesExactlyOneActivePlan() throws Exception {
        List<Long> ids=new TransactionTemplate(tx).execute(status->{
            Patient patient=patients.findByAccountUsername("patient2").orElseThrow();Exercise exercise=exercises.findAll().get(0);
            List<Long> result=new ArrayList<>();
            for(int i=0;i<2;i++) {
                RehabPlan p=new RehabPlan();p.patient=patient;p.title="План "+i;p.goal="Тест конкурентности";p.startsOn=LocalDate.now();p.endsOn=p.startsOn.plusDays(7);plans.save(p);
                Assignment a=new Assignment();a.plan=p;a.exercise=exercise;a.title=exercise.title;a.instructions=exercise.instructions;a.unit=exercise.unit;a.targetAmount=10;assignments.save(a);result.add(p.id);
            }return result;
        });
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        List<Future<String>> futures=new ArrayList<>();
        try {
            for(Long id:ids)futures.add(pool.submit(()->{
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("doctor","",List.of(new SimpleGrantedAuthority("ROLE_SPECIALIST"))));
                try {ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Timeout");
                    service.changeStatus(id,new StatusInput(RehabPlan.Status.ACTIVE,0L));return "active";
                }catch(BusinessException e){if(e.status!=409)throw e;return "conflict";}
                finally{SecurityContextHolder.clearContext();}
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("active","conflict");
            Long patientId=patients.findByAccountUsername("patient2").orElseThrow().id;
            assertThat(plans.findByPatientIdOrderByIdDesc(patientId).stream().filter(p->p.status==RehabPlan.Status.ACTIVE).count()).isEqualTo(1);
        }finally{go.countDown();pool.shutdownNow();}
    }
}
