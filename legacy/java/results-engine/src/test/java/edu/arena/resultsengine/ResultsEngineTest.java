package edu.arena.resultsengine;
import edu.arena.common.*;
import edu.arena.common.domain.*;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ResultsEngineTest {
    Store store; Events events; Json json; ResultsEngine engine; MatchRow match; Account user; Bet bet; Instant start;
    @BeforeEach void setup() {
        store=mock(Store.class); events=mock(Events.class); json=new Json(JsonMapper.builder().findAndAddModules().build()); engine=new ResultsEngine(store,events,json);
        start=Instant.parse("2026-01-01T00:00:00Z"); match=new MatchRow(UUID.randomUUID(),"A","B",start,start.plusSeconds(60));
        user=new Account(UUID.randomUUID(),"a@arena.test","A","hash","USER"); user.debit(new BigDecimal("100"));
        bet=new Bet(UUID.randomUUID(),match.id,user.id,"HOME",new BigDecimal("100"),start.plusSeconds(10),UUID.randomUUID());
        when(store.lock(MatchRow.class,match.id)).thenReturn(match); when(store.lock(Account.class,user.id)).thenReturn(user); when(store.get(Bet.class,bet.id)).thenReturn(bet);
    }
    EventRow journal(ArenaEvent event,long offset) { return new EventRow(event.eventId(),event.matchId(),offset,json.write(event)); }
    @Test void settlesWinAndPreventsDoubleCredit() {
        bet.status="ACCEPTED";
        when(store.bets(match.id)).thenReturn(List.of(bet));
        var created=ArenaEvent.created(match.id,"A","B",start,match.closesAt);
        var placed=ArenaEvent.bet(match.id,bet.id,user.id,"HOME",bet.stake,bet.placedAt);
        when(store.events(match.id)).thenReturn(List.of(journal(created,0),journal(placed,1)));
        var finished=ArenaEvent.finished(match.id,"HOME",match.closesAt);
        engine.process(finished,2,json.write(finished));
        assertEquals(new BigDecimal("10100.00"),user.balance); assertEquals("WON",bet.status); assertEquals("FINISHED",match.status);
        when(store.get(EventRow.class,finished.eventId())).thenReturn(journal(finished,2));
        engine.process(finished,2,json.write(finished)); assertEquals(new BigDecimal("10100.00"),user.balance); verify(store,times(1)).save(isA(Settlement.class));
    }
    @Test void lateEventAfterFinishRefundsReservation() {
        var created=ArenaEvent.created(match.id,"A","B",start,match.closesAt); var finished=ArenaEvent.finished(match.id,"AWAY",match.closesAt);
        when(store.events(match.id)).thenReturn(List.of(journal(created,0),journal(finished,1)));
        var placed=ArenaEvent.bet(match.id,bet.id,user.id,"HOME",bet.stake,bet.placedAt); engine.process(placed,2,json.write(placed));
        assertEquals("REJECTED",bet.status); assertEquals(new BigDecimal("10000.00"),user.balance);
        verify(events).notify(eq(match.id),eq("notify.email.loss"),argThat(n->n.outcome().equals("REJECTED")));
    }
    @Test void persistedSettlementPreventsReplayPayout() {
        bet.status="ACCEPTED"; when(store.bets(match.id)).thenReturn(List.of(bet));
        when(store.get(Settlement.class,bet.id)).thenReturn(new Settlement(bet,"WON",new BigDecimal("200"),Instant.now()));
        var finish=ArenaEvent.finished(match.id,"HOME",match.closesAt); engine.process(finish,2,json.write(finish));
        assertEquals(new BigDecimal("9900.00"),user.balance); assertEquals("WON",bet.status); verify(store,never()).save(isA(Settlement.class));
    }
    @Test void losingBetDoesNotCreditBalance() {
        bet.status="ACCEPTED"; when(store.bets(match.id)).thenReturn(List.of(bet)); var finish=ArenaEvent.finished(match.id,"AWAY",match.closesAt);
        engine.process(finish,2,json.write(finish)); assertEquals("LOST",bet.status); assertEquals(new BigDecimal("9900.00"),user.balance);
    }
    @Test void replayedBetDoesNotResetAnExistingSettlement() {
        bet.status="WON";
        when(store.get(Settlement.class,bet.id)).thenReturn(new Settlement(bet,"WON",new BigDecimal("200"),Instant.now()));
        var event=ArenaEvent.bet(match.id,bet.id,user.id,"HOME",bet.stake,bet.placedAt);
        engine.process(event,1,json.write(event));
        assertEquals("WON",bet.status); assertEquals(new BigDecimal("9900.00"),user.balance);
        verify(store,never()).save(isA(Settlement.class));
    }
}
