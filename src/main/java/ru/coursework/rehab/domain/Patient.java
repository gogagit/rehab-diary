package ru.coursework.rehab.domain;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity @Table(name="patients")
public class Patient {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @OneToOne(optional=false) @JoinColumn(unique=true) public Account account;
    @ManyToOne public Account specialist;
    @Column(nullable=false) public LocalDate birthDate;
    @Column(nullable=false,length=200) public String rehabilitationReason;
    @Column(length=2000) public String notes;
}
