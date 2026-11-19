package ru.coursework.rehab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trusted_devices", indexes = @Index(name = "trusted_devices_account_idx", columnList = "account_id"))
public class TrustedDevice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) public Account account;
    @Column(nullable = false, unique = true, length = 64) public String tokenHash;
    @Column(nullable = false, length = 80) public String name;
    @Column(nullable = false) public Instant expiresAt;
    public Instant linkedAt;
}
