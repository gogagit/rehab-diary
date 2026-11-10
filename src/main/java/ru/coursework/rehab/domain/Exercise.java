package ru.coursework.rehab.domain;
import jakarta.persistence.*;
@Entity @Table(name="exercises")
public class Exercise {
    public enum Unit { REPETITIONS, MINUTES, METERS }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false,length=120) public String title;
    @Column(nullable=false,length=2000) public String instructions;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Unit unit;
}
