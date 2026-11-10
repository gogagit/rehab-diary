package ru.coursework.rehab.repository;
import ru.coursework.rehab.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EntryRepository extends JpaRepository<DiaryEntry,Long> { java.util.List<DiaryEntry> findByAssignmentPlanIdOrderByPerformedOnDescIdDesc(Long planId);
boolean existsByAssignmentIdAndPerformedOn(Long assignmentId,java.time.LocalDate date); }
