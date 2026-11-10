package ru.coursework.rehab.domain;
import jakarta.persistence.*;
@Entity @Table(name="assignments",uniqueConstraints=@UniqueConstraint(columnNames={"plan_id","exercise_id"}))
public class Assignment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) public RehabPlan plan;
    @ManyToOne(optional=false) public Exercise exercise;
    // Снимок назначения сохраняет историю при последующем изменении справочника.
    @Column(nullable=false,length=120) public String title;
    @Column(nullable=false,length=2000) public String instructions;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Exercise.Unit unit;
    @Column(nullable=false) public double targetAmount;
}
