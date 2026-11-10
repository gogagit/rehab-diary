package ru.coursework.rehab.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name="chat_messages", indexes=@Index(name="chat_thread_idx",columnList="patient_id,channel,peer_key,id"))
public class ChatMessage {
    public enum Channel { DOCTOR, SUPPORT }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) public Patient patient;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Channel channel;
    // Specialist account ID for a clinical thread, zero for the shared support inbox.
    @Column(name="peer_key",nullable=false) public long peerKey;
    @Column(nullable=false) public boolean fromPatient;
    @Column(nullable=false,length=150) public String senderName;
    @Column(nullable=false,length=2000) public String body;
    @Column(nullable=false) public OffsetDateTime sentAt;
    public OffsetDateTime readAt;
}
