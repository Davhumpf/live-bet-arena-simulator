package edu.arena.common;
import edu.arena.common.domain.*;
import edu.arena.common.persistence.Rows.Account;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class DomainTest {
    private final UUID id=UUID.randomUUID();
    private final Instant now=Instant.parse("2026-01-01T00:00:00Z");
    @Test void replayAndDuplicateEvents() {
        var created=ArenaEvent.created(id,"A","B",now,now.plusSeconds(60));
        var bet=ArenaEvent.bet(id,UUID.randomUUID(),UUID.randomUUID(),"HOME",new BigDecimal("100"),now.plusSeconds(5));
        var finished=ArenaEvent.finished(id,"HOME",now.plusSeconds(60));
        var match=new Match(new MatchId(id),created.closesAt());
        assertTrue(match.apply(created)); assertTrue(match.apply(bet)); assertFalse(match.apply(bet)); match.apply(finished);
        var replay=new Match(new MatchId(id),created.closesAt()); List.of(created,bet,finished).forEach(replay::apply);
        assertEquals(match.status(),replay.status()); assertEquals(match.winner(),replay.winner()); assertEquals(1,replay.bets().size());
    }
    @Test void rejectsDeadlineAndFinishedBets() {
        var match=new Match(new MatchId(id),now.plusSeconds(60));
        assertFalse(match.apply(ArenaEvent.bet(id,UUID.randomUUID(),UUID.randomUUID(),"HOME",BigDecimal.ONE,now.plusSeconds(60))));
        match.apply(ArenaEvent.finished(id,"AWAY",now.plusSeconds(60)));
        assertFalse(match.apply(ArenaEvent.bet(id,UUID.randomUUID(),UUID.randomUUID(),"HOME",BigDecimal.ONE,now.plusSeconds(10))));
    }
    @Test void stateTransitionsAndImmutableResult() {
        var match=new Match(new MatchId(id),now.plusSeconds(60)); match.close(now.plusSeconds(59));
        assertEquals(Match.Status.OPEN_FOR_BETS,match.status()); match.close(now.plusSeconds(60));
        assertEquals(Match.Status.CLOSED,match.status()); match.apply(ArenaEvent.finished(id,"AWAY",now.plusSeconds(60)));
        assertEquals(Match.Status.FINISHED,match.status());
        assertThrows(IllegalStateException.class,()->match.apply(ArenaEvent.finished(id,"HOME",now.plusSeconds(61))));
    }
    @Test void preventsEarlyFinish() { var match=new Match(new MatchId(id),now.plusSeconds(60)); assertThrows(IllegalArgumentException.class,()->match.apply(ArenaEvent.finished(id,"HOME",now))); }
    @Test void settlementReturnsStakeAndProfit() {
        var policy=new SettlementPolicy.EvenOdds(); assertEquals(new BigDecimal("200.00"),policy.payout(new BigDecimal("100.00"),true));
        assertEquals(new BigDecimal("0.00"),policy.payout(new BigDecimal("100.00"),false));
        assertThrows(IllegalArgumentException.class,()->policy.payout(BigDecimal.ZERO,true));
    }
    @Test void balanceValidation() {
        var user=new Account(UUID.randomUUID(),"a@arena.test","A","hash","USER"); user.debit(new BigDecimal("100"));
        assertEquals(new BigDecimal("9900.00"),user.balance); user.credit(new BigDecimal("200")); assertEquals(new BigDecimal("10100.00"),user.balance);
        assertThrows(IllegalArgumentException.class,()->user.debit(new BigDecimal("20000")));
        assertThrows(IllegalArgumentException.class,()->user.debit(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,()->user.debit(new BigDecimal("1.001")));
    }
}
