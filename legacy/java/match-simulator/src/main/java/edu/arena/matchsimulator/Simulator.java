package edu.arena.matchsimulator;
import edu.arena.common.*;
import edu.arena.common.domain.ArenaEvent;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
@Service
public class Simulator {
    private final Store store; private final Events events; private final WinnerStrategy winner;
    public Simulator(Store store,Events events,WinnerStrategy winner) { this.store=store; this.events=events; this.winner=winner; }
    @RabbitListener(queues="match_simulation_queue") @Transactional
    public void finish(String matchId) {
        var match=store.lock(MatchRow.class,UUID.fromString(matchId));
        if (match==null) throw new IllegalStateException("Partido todavía no disponible");
        if (match.finishRequested) return;
        var now=Instant.now();
        if (now.isBefore(match.closesAt)) { events.timer(match.id,match.closesAt); return; }
        match.status="CLOSED"; match.finishRequested=true;
        events.publish(ArenaEvent.finished(match.id,winner.select(),now));
    }
}
