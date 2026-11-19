package edu.arena.matchgenerator;
import edu.arena.common.*;
import edu.arena.common.domain.ArenaEvent;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class GeneratorTest {
    @Test void createsDistinctTeamsAndRealTimerCommand() {
        var store=mock(Store.class); var events=mock(Events.class); new Generator(store,events).generate();
        var event=ArgumentCaptor.forClass(ArenaEvent.class); verify(events).publish(event.capture());
        var created=event.getValue(); assertEquals("MATCH_CREATED",created.type()); assertNotEquals(created.home(),created.away());
        assertEquals(Duration.ofSeconds(60),Duration.between(created.occurredAt(),created.closesAt()));
        verify(events).timer(created.matchId(),created.closesAt()); verify(store).save(isA(MatchRow.class));
    }
}
