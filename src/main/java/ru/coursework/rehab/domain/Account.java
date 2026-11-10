package ru.coursework.rehab.domain;
import jakarta.persistence.*;
@Entity @Table(name="accounts")
public class Account {
    public java.time.OffsetDateTime deletedAt;
    public enum Role { ADMIN, SPECIALIST, PATIENT }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false,unique=true,length=254) public String username;
    @Column(nullable=false) public String passwordHash;
    @Column(nullable=false,length=150) public String fullName;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Role role;
}
