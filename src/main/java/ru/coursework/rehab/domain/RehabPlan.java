package ru.coursework.rehab.domain;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity @Table(name="rehab_plans")
public class RehabPlan {
    public enum Status { DRAFT, ACTIVE, COMPLETED, ARCHIVED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) public Patient patient;
    @Column(nullable=false,length=150) public String title;
    @Column(nullable=false,length=1000) public String goal;
    @Column(nullable=false) public LocalDate startsOn;
    @Column(nullable=false) public LocalDate endsOn;
    public LocalDate closedOn;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Status status=Status.DRAFT;
    @Column(length=1000) public String review;
    public boolean finished() {
        return status==Status.COMPLETED || (status==Status.ARCHIVED && closedOn!=null && !closedOn.isBefore(startsOn));
    }
    @Version public long version;
}
