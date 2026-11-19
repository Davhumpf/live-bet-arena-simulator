package edu.arena.bettingapi;
import edu.arena.common.*;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BettingServiceTest {
    Store store; Events events; BettingService service; MatchRow match; Account user;
    @BeforeEach void setup() {
        store=mock(Store.class); events=mock(Events.class); service=new BettingService(store,events);
        match=new MatchRow(UUID.randomUUID(),"A","B",Instant.now(),Instant.now().plusSeconds(60));
        user=new Account(UUID.randomUUID(),"a@arena.test","A","hash","USER");
        when(store.lock(MatchRow.class,match.id)).thenReturn(match); when(store.lockByEmail(user.email)).thenReturn(user);
        when(store.events(match.id)).thenReturn(List.of(new EventRow(UUID.randomUUID(),match.id,0,"{}")));
    }
    @Test void reservesAndPublishes() { var bet=service.place(user.email,match.id,"HOME",new BigDecimal("100"),UUID.randomUUID()); assertEquals("PENDING",bet.status); assertEquals(new BigDecimal("9900.00"),user.balance); verify(events).publish(any()); verify(store).save(isA(Ledger.class)); }
    @Test void rejectsLate() { match.closesAt=Instant.now().minusSeconds(1); assertThrows(IllegalStateException.class,()->service.place(user.email,match.id,"HOME",BigDecimal.ONE,UUID.randomUUID())); verifyNoInteractions(events); }
    @Test void rejectsInsufficientBalance() { assertThrows(IllegalArgumentException.class,()->service.place(user.email,match.id,"AWAY",new BigDecimal("10001"),UUID.randomUUID())); verifyNoInteractions(events); }
    @Test void rejectsNegativeStake() { assertThrows(IllegalArgumentException.class,()->service.place(user.email,match.id,"HOME",BigDecimal.ONE.negate(),UUID.randomUUID())); }
    @Test void retriesDoNotDebitTwiceEvenAfterClose() {
        var request=UUID.randomUUID(); var bet=new Bet(UUID.randomUUID(),match.id,user.id,"HOME",BigDecimal.TEN,Instant.now(),request);
        when(store.byRequest(request)).thenReturn(bet); match.closesAt=Instant.now().minusSeconds(1);
        assertSame(bet,service.place(user.email,match.id,"HOME",BigDecimal.TEN,request)); assertEquals(new BigDecimal("10000.00"),user.balance); verifyNoInteractions(events);
    }
    @Test void preventsReusingRequestWithChangedStake() {
        var request=UUID.randomUUID(); when(store.byRequest(request)).thenReturn(new Bet(UUID.randomUUID(),match.id,user.id,"HOME",BigDecimal.TEN,Instant.now(),request));
        assertThrows(IllegalStateException.class,()->service.place(user.email,match.id,"HOME",BigDecimal.ONE,request));
    }
}
