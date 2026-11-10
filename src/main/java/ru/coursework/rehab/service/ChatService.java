package ru.coursework.rehab.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coursework.rehab.domain.*;
import ru.coursework.rehab.domain.ChatMessage.Channel;
import ru.coursework.rehab.repository.*;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class ChatService {
    private final ChatRepository messages;
    private final PatientRepository patients;
    private final RehabService rehab;
    private final Clock clock;

    public ChatService(ChatRepository messages,PatientRepository patients,RehabService rehab,Clock clock) {
        this.messages=messages; this.patients=patients; this.rehab=rehab; this.clock=clock;
    }

    public record ThreadView(Long patientId,Channel channel,String title,long unread) {}
    public record MessageView(Long id,String senderName,String body,OffsetDateTime sentAt,boolean mine,boolean unread) {
        static MessageView of(ChatMessage m,boolean patientSide) {
            boolean mine=m.fromPatient==patientSide;
            return new MessageView(m.id,m.senderName,m.body,m.sentAt,mine,!mine && m.readAt==null);
        }
    }
    public record History(List<MessageView> messages,boolean hasOlder) {}
    private record Key(Long patientId,Channel channel,long peer) {}
    private record Scope(Patient patient,boolean patientSide,long peer) {}

    public List<ThreadView> threads() {
        Account a=rehab.actor();
        boolean patientSide=a.role==Account.Role.PATIENT;
        List<Patient> cards=switch(a.role) {
            case PATIENT -> patients.findByAccountUsername(a.username).stream().toList();
            case SPECIALIST -> patients.findBySpecialistUsernameOrderByIdDesc(a.username);
            case ADMIN -> patients.findAll();
        };
        if(cards.isEmpty())return List.of();
        Map<Key,Long> unread=new HashMap<>();
        for(Object[] row:messages.unread(cards.stream().map(p->p.id).toList(),!patientSide))
            unread.put(new Key((Long)row[0],(Channel)row[1],(Long)row[2]),(Long)row[3]);
        List<ThreadView> result=new ArrayList<>();
        for(Patient p:cards) {
            if(a.role!=Account.Role.ADMIN && p.specialist!=null)
                result.add(thread(p,Channel.DOCTOR,p.specialist.id,patientSide?p.specialist.fullName:p.account.fullName,unread));
            if(a.role!=Account.Role.SPECIALIST)
                result.add(thread(p,Channel.SUPPORT,0,patientSide?"Техподдержка":p.account.fullName,unread));
        }
        result.sort(Comparator.comparingLong(ThreadView::unread).reversed());
        return result;
    }

    private ThreadView thread(Patient p,Channel channel,long peer,String title,Map<Key,Long> unread) {
        return new ThreadView(p.id,channel,title,unread.getOrDefault(new Key(p.id,channel,peer),0L));
    }
    private Scope scope(Long id,Channel channel,boolean write) {
        Account a=rehab.actor();
        Patient p=(write?patients.findLockedById(id):patients.findById(id)).orElseThrow(BusinessException::missing);
        boolean owner=a.role==Account.Role.PATIENT && a.id.equals(p.account.id);
        if(channel==Channel.SUPPORT && (owner || a.role==Account.Role.ADMIN))return new Scope(p,owner,0);
        if(channel==Channel.DOCTOR && p.specialist!=null &&
            (owner || (a.role==Account.Role.SPECIALIST && a.id.equals(p.specialist.id))))
            return new Scope(p,owner,p.specialist.id);
        throw new AccessDeniedException("Нет доступа к этому диалогу");
    }

    public History history(Long id,Channel channel,long before) {
        Scope scope=scope(id,channel,false);
        var rows=messages.findByPatientIdAndChannelAndPeerKeyAndIdLessThanOrderByIdDesc(
            id,channel,scope.peer,before,PageRequest.of(0,101));
        var page=new ArrayList<>(rows.subList(0,Math.min(100,rows.size())));
        Collections.reverse(page);
        return new History(page.stream().map(m->MessageView.of(m,scope.patientSide)).toList(),rows.size()>100);
    }
    @Transactional public MessageView send(Long id,Channel channel,String text) {
        Scope scope=scope(id,channel,true);
        ChatMessage m=new ChatMessage();
        m.patient=scope.patient; m.channel=channel; m.peerKey=scope.peer; m.fromPatient=scope.patientSide;
        m.senderName=rehab.actor().fullName; m.body=text.strip(); m.sentAt=OffsetDateTime.now(clock);
        return MessageView.of(messages.save(m),scope.patientSide);
    }
    @Transactional public void read(Long id,Channel channel,List<Long> ids) {
        Scope scope=scope(id,channel,true);
        messages.markRead(id,channel,scope.peer,!scope.patientSide,ids,OffsetDateTime.now(clock));
    }
}
