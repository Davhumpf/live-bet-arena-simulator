package edu.arena.matchsimulator;
import edu.arena.common.*;
import edu.arena.common.domain.ArenaEvent;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SimulatorTest {
    @Test void defaultWinnerNeedsNoServiceLoaderProvider() {
        assertTrue(Set.of("HOME","AWAY").contains(new WinnerStrategy.RandomWinner().select()));
    }
    @Test void closesOnceUsingInjectedWinner() {
        var store=mock(Store.class); var events=mock(Events.class); var winner=mock(WinnerStrategy.class);
        var match=new MatchRow(UUID.randomUUID(),"A","B",Instant.now().minusSeconds(61),Instant.now().minusSeconds(1));
        when(store.lock(MatchRow.class,match.id)).thenReturn(match); when(winner.select()).thenReturn("HOME");
        var simulator=new Simulator(store,events,winner); simulator.finish(match.id.toString()); simulator.finish(match.id.toString());
        assertEquals("CLOSED",match.status); assertTrue(match.finishRequested); verify(winner,times(1)).select();
        verify(events).publish(argThat(e->e.type().equals("MATCH_FINISHED")&&e.winner().equals("HOME")));
    }
    @Test void earlyDeliveryIsRescheduledInRabbit() {
        var store=mock(Store.class); var events=mock(Events.class); var winner=mock(WinnerStrategy.class);
        var match=new MatchRow(UUID.randomUUID(),"A","B",Instant.now(),Instant.now().plusSeconds(30));
        when(store.lock(MatchRow.class,match.id)).thenReturn(match);
        new Simulator(store,events,winner).finish(match.id.toString()); verify(events).timer(match.id,match.closesAt); verifyNoInteractions(winner); assertFalse(match.finishRequested);
    }
    @Test void seededRandomIsReproducible() {
        var first=new WinnerStrategy.RandomWinner(new Random(42)); var second=new WinnerStrategy.RandomWinner(new Random(42));
        for(int i=0;i<100;i++) assertEquals(first.select(),second.select());
    }
}
