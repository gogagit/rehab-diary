package ru.coursework.rehab.repository;
import ru.coursework.rehab.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AssignmentRepository extends JpaRepository<Assignment,Long> { java.util.List<Assignment> findByPlanIdOrderById(Long planId); boolean existsByPlanIdAndExerciseId(Long planId,Long exerciseId); }
