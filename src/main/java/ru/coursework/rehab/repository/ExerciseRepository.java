package ru.coursework.rehab.repository;
import ru.coursework.rehab.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ExerciseRepository extends JpaRepository<Exercise,Long> { java.util.List<Exercise> findAllByOrderByTitle(); }
