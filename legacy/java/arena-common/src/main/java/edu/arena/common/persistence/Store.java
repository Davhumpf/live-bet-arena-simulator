package edu.arena.common.persistence;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.*;
import edu.arena.common.persistence.Rows.*;
@Repository
public class Store {
    @PersistenceContext private EntityManager em;
    public <T> T get(Class<T> type,Object id) { return em.find(type,id); }
    public <T> T lock(Class<T> type,Object id) { return em.find(type,id,LockModeType.PESSIMISTIC_WRITE); }
    public void save(Object row) { em.persist(row); }
    public void flush() { em.flush(); }
    public Account byEmail(String email) { return em.createQuery("from Account where email=:email",Account.class).setParameter("email",email).getResultList().stream().findFirst().orElse(null); }
    public Account lockByEmail(String email) { return em.createQuery("from Account where email=:email",Account.class).setParameter("email",email).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList().stream().findFirst().orElse(null); }
    public Bet byRequest(UUID id) { return em.createQuery("from Bet where requestId=:id",Bet.class).setParameter("id",id).getResultList().stream().findFirst().orElse(null); }
    public List<Bet> bets(UUID matchId) { return em.createQuery("from Bet where matchId=:id order by placedAt, id",Bet.class).setParameter("id",matchId).getResultList(); }
    public List<EventRow> events(UUID matchId) { return em.createQuery("from EventRow where matchId=:id order by eventOffset",EventRow.class).setParameter("id",matchId).getResultList(); }
    public List<Outbox> pending(String owner) {
        return em.createQuery("from Outbox where owner=:owner and sent=false order by id",Outbox.class).setParameter("owner",owner).setMaxResults(50).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }
    public List<MatchRow> matches() { return em.createQuery("from MatchRow order by createdAt desc",MatchRow.class).setMaxResults(60).getResultList(); }
    public List<Bet> history(UUID user) { return em.createQuery("from Bet where userId=:id order by placedAt desc",Bet.class).setParameter("id",user).setMaxResults(100).getResultList(); }
    public List<Ledger> ledger(UUID user) { return em.createQuery("from Ledger where userId=:id order by createdAt desc",Ledger.class).setParameter("id",user).setMaxResults(100).getResultList(); }
    public List<Account> accounts() { return em.createQuery("from Account order by name",Account.class).getResultList(); }
    public List<Audit> activity() { return em.createQuery("from Audit order by createdAt desc",Audit.class).setMaxResults(25).getResultList(); }
    public Map<UUID,Long> counts() {
        Map<UUID,Long> result=new HashMap<>();
        em.createQuery("select matchId, count(*) from Bet group by matchId",Object[].class).getResultList().forEach(r->result.put((UUID)r[0],(Long)r[1])); return result;
    }
}
