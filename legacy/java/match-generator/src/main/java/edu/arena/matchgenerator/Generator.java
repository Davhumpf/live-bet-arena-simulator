package edu.arena.matchgenerator;
import edu.arena.common.*;
import edu.arena.common.domain.ArenaEvent;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service
public class Generator {
    private static final List<String> TEAMS=List.of("Cóndores FC","Atlético Aurora","Lobos del Norte","Deportivo Solaris","Titanes del Sur","Unión Pacífico","Águilas Doradas","Racing Eclipse","Inter Andino","Real Cumbre","Estrella Azul","Sporting Volcán");
    private final Store store; private final Events events; private final Random random=new Random();
    public Generator(Store store,Events events) { this.store=store; this.events=events; }
    @Scheduled(fixedDelayString="${arena.generation-ms:10000}",initialDelay=5000) @Transactional
    public void generate() {
        var teams=new ArrayList<>(TEAMS); Collections.shuffle(teams,random);
        var now=Instant.now(); var close=now.plusSeconds(60); var id=UUID.randomUUID();
        store.save(new MatchRow(id,teams.get(0),teams.get(1),now,close));
        events.publish(ArenaEvent.created(id,teams.get(0),teams.get(1),now,close));
        events.timer(id,close);
        store.save(new Audit("MATCH_CREATED",teams.get(0)+" vs "+teams.get(1)));
    }
}
