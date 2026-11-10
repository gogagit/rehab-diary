package ru.coursework.rehab.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import ru.coursework.rehab.domain.ChatMessage;
import ru.coursework.rehab.domain.ChatMessage.Channel;
import java.time.OffsetDateTime;
import java.util.List;

public interface ChatRepository extends JpaRepository<ChatMessage,Long> {
    List<ChatMessage> findByPatientIdAndChannelAndPeerKeyAndIdLessThanOrderByIdDesc(
        Long patientId,Channel channel,long peerKey,long before,Pageable page);

    @Query("""
        select m.patient.id, m.channel, m.peerKey, count(m) from ChatMessage m
        where m.patient.id in :ids and m.fromPatient = :incoming and m.readAt is null
        group by m.patient.id,m.channel,m.peerKey
        """)
    List<Object[]> unread(List<Long> ids,boolean incoming);

    @Modifying(flushAutomatically=true,clearAutomatically=true)
    @Query("""
        update ChatMessage m set m.readAt = :now where m.patient.id = :id and m.channel = :channel
        and m.peerKey = :peer and m.fromPatient = :incoming and m.readAt is null and m.id in :ids
        """)
    void markRead(Long id,Channel channel,long peer,boolean incoming,List<Long> ids,OffsetDateTime now);
}

