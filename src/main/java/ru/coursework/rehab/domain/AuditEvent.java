package ru.coursework.rehab.domain;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
@Entity @Table(name="audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public OffsetDateTime occurredAt;
    @Column(nullable=false) public String actor;
    @Column(nullable=false) public String action;
    @Column(nullable=false) public String entityType;
    public Long entityId;
}
