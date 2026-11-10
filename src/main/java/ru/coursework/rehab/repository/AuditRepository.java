package ru.coursework.rehab.repository;
import ru.coursework.rehab.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuditRepository extends JpaRepository<AuditEvent,Long> {  }
