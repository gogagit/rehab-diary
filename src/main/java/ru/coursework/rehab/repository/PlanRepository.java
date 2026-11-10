package ru.coursework.rehab.repository;
import ru.coursework.rehab.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlanRepository extends JpaRepository<RehabPlan,Long> { java.util.List<RehabPlan> findByPatientIdOrderByIdDesc(Long patientId);
@org.springframework.data.jpa.repository.Query("select p from RehabPlan p where p.patient.id in :ids and p.closedOn >= p.startsOn order by p.closedOn desc, p.id desc")
java.util.List<RehabPlan> findFinishedByPatientIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
boolean existsByPatientIdAndStatus(Long patientId, RehabPlan.Status status); }
