package edu.arena.common;
import edu.arena.common.domain.ArenaEvent;
import edu.arena.common.persistence.Store;
import edu.arena.common.persistence.Rows.Outbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;
@Component
public class Events {
    private final Store store; private final Json json; private final String owner;
    public Events(Store store,Json json,@Value("${spring.application.name}") String owner) { this.store=store; this.json=json; this.owner=owner; }
    public void publish(ArenaEvent event) { store.save(new Outbox(owner,event.matchId(),"kafka","match_events",json.write(event),null)); }
    public void timer(UUID matchId,Instant dueAt) { store.save(new Outbox(owner,matchId,"timer","match.finish",matchId.toString(),dueAt)); }
    public void notify(UUID matchId,String key,Notification notification) { store.save(new Outbox(owner,matchId,"notification",key,json.write(notification),null)); }
}
