package ru.coursework.rehab.repository;
import ru.coursework.rehab.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PatientRepository extends JpaRepository<Patient,Long> { java.util.List<Patient> findBySpecialistUsernameOrderByIdDesc(String username);
java.util.Optional<Patient> findByAccountUsername(String username);
@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
@org.springframework.data.jpa.repository.Query("select p from Patient p where p.id = :id")
java.util.Optional<Patient> findLockedById(@org.springframework.data.repository.query.Param("id") Long id); }
