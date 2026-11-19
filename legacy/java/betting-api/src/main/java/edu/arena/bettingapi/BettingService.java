package edu.arena.bettingapi;
import edu.arena.common.*;
import edu.arena.common.domain.ArenaEvent;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Service
public class BettingService {
    private final Store store; private final Events events;
    public BettingService(Store store,Events events) { this.store=store; this.events=events; }
    @Transactional
    public Bet place(String email,UUID matchId,String selection,BigDecimal stake,UUID requestId) {
        if (!Set.of("HOME","AWAY").contains(selection)) throw new IllegalArgumentException("Selecciona HOME o AWAY");
        if (stake==null || stake.signum()<=0 || stake.scale()>2 || stake.compareTo(new BigDecimal("1000000"))>0) throw new IllegalArgumentException("Cantidad inválida");
        var match=store.lock(MatchRow.class,matchId);
        if (match==null) throw new IllegalArgumentException("Partido desconocido");
        var user=store.lockByEmail(email);
        if (user==null) throw new IllegalArgumentException("Usuario desconocido");
        var previous=store.byRequest(requestId);
        if (previous!=null) {
            if (!previous.userId.equals(user.id) || !previous.matchId.equals(matchId) || !previous.selection.equals(selection) || previous.stake.compareTo(stake)!=0)
                throw new IllegalStateException("La clave de idempotencia ya tiene otra apuesta");
            return previous;
        }
        var now=Instant.now();
        if (!match.openAt(now)) throw new IllegalStateException("El partido ya cerró");
        if (store.events(matchId).isEmpty()) throw new IllegalStateException("Partido sincronizándose; vuelve a intentar");
        user.debit(stake);
        var bet=new Bet(UUID.randomUUID(),matchId,user.id,selection,stake,now,Objects.requireNonNull(requestId));
        store.save(bet); store.save(new Ledger(user.id,bet.id,stake.negate(),"STAKE_RESERVED"));
        events.publish(ArenaEvent.bet(matchId,bet.id,user.id,selection,stake,now));
        store.save(new Audit("BET_PLACED",bet.id+" · "+stake+" puntos"));
        return bet;
    }
}
