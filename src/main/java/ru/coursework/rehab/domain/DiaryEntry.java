package ru.coursework.rehab.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="diary_entries",uniqueConstraints=@UniqueConstraint(columnNames={"assignment_id","performed_on"}))
public class DiaryEntry {
    public enum Status { DONE, SKIPPED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) public Assignment assignment;
    @Column(name="performed_on",nullable=false) public LocalDate performedOn;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Status status;
    public double actualAmount;
    public int pain;
    public int wellbeing;
    @Column(nullable=false,length=2000) public String comment;
    @Column(length=2000) public String feedback;
    public OffsetDateTime updatedAt;
    @Version public long version;
}
