package edu.arena.resultsengine;
import edu.arena.common.*;
import edu.arena.common.domain.*;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class ResultsEngine {
    private final Store store; private final Events events; private final Json json;
    private final SettlementPolicy policy=new SettlementPolicy.EvenOdds();
    private final Map<MatchId,Match> aggregates=new ConcurrentHashMap<>();
    public ResultsEngine(Store store,Events events,Json json) { this.store=store; this.events=events; this.json=json; }
    @KafkaListener(topics="match_events") @Transactional
    public void consume(ConsumerRecord<String,String> record) {
        process(json.read(record.value(),ArenaEvent.class),record.offset(),record.value());
    }
    public void process(ArenaEvent event,long offset,String payload) {
        var row=store.lock(MatchRow.class,event.matchId());
        if (row==null) throw new IllegalStateException("Proyección de partido no disponible");
        if (store.get(EventRow.class,event.eventId())!=null) return;
        var id=new MatchId(event.matchId());
                                                                                                                 
        var aggregate=new Match(id,row.closesAt);
        store.events(row.id).forEach(e->aggregate.apply(json.read(e.payload,ArenaEvent.class)));
        boolean accepted=aggregate.apply(event);
        store.save(new EventRow(event.eventId(),event.matchId(),offset,payload));
        if (event.type().equals("BET_PLACED")) {
            var bet=store.get(Bet.class,event.betId());
            if (bet==null) throw new IllegalStateException("Reserva de apuesta no disponible");
            if (!bet.matchId.equals(event.matchId()) || !bet.userId.equals(event.userId()) || bet.stake.compareTo(event.stake())!=0 || !bet.selection.equals(event.selection()))
                throw new IllegalArgumentException("Evento no coincide con la reserva");
            var existing=store.get(Settlement.class,bet.id);
            if (existing!=null) bet.status=existing.outcome;
            else if (accepted) bet.status="ACCEPTED";
            else settle(bet,"REJECTED",bet.stake);
        }
        if (event.type().equals("MATCH_FINISHED")) {
            row.status="FINISHED"; row.winner=event.winner();
            for (var bet:store.bets(row.id)) {
                if (bet.status.equals("ACCEPTED")) {
                    boolean won=bet.selection.equals(event.winner());
                    settle(bet,won?"WON":"LOST",policy.payout(bet.stake,won));
                }
            }
            store.save(new Audit("MATCH_FINISHED",row.home+" vs "+row.away+" · "+row.winner));
        }
        events.notify(row.id,"notify.dashboard.update",new Notification(event.eventId().toString(),null,event.type(),null,null));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { aggregates.put(id,aggregate); }
            });
        }
    }
    private void settle(Bet bet,String outcome,BigDecimal payout) {
        var existing=store.get(Settlement.class,bet.id);
        if (existing!=null) { bet.status=existing.outcome; return; }
        var user=store.lock(Account.class,bet.userId);
        user.credit(payout); bet.status=outcome;
        store.save(new Settlement(bet,outcome,payout,Instant.now()));
        if (payout.signum()>0) store.save(new Ledger(user.id,bet.id,payout,outcome.equals("REJECTED")?"STAKE_REFUND":"PAYOUT"));
        events.notify(bet.matchId,outcome.equals("WON")?"notify.email.win":"notify.email.loss",
            new Notification(bet.id+":"+outcome,user.id,outcome,bet.stake,payout));
        store.save(new Audit("BET_"+outcome,bet.id+" · retorno "+payout+" puntos"));
    }
}
