package edu.arena.common.persistence;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public final class Rows {
    private Rows() { }
    @Entity(name="Account") @Table(name="accounts")
    public static class Account {
        @Id public UUID id;
        @Column(unique=true, nullable=false) public String email;
        public String name;
        public String password;
        public String role;
        @Column(precision=19,scale=2) public BigDecimal balance;
        protected Account() { }
        public Account(UUID id, String email, String name, String password, String role) {
            this.id=id; this.email=email; this.name=name; this.password=password; this.role=role; balance=new BigDecimal("10000.00");
        }
        public void debit(BigDecimal amount) {
            if (amount == null || amount.signum() <= 0 || amount.scale() > 2) throw new IllegalArgumentException("Cantidad positiva con máximo dos decimales");
            if (balance.compareTo(amount) < 0) throw new IllegalArgumentException("Saldo insuficiente");
            balance=balance.subtract(amount);
        }
        public void credit(BigDecimal amount) {
            if (amount.signum()<0) throw new IllegalArgumentException("Crédito negativo");
            balance=balance.add(amount);
        }
    }
    @Entity(name="MatchRow") @Table(name="matches")
    public static class MatchRow {
        @Id public UUID id;
        public String home;
        public String away;
        public Instant createdAt;
        public Instant closesAt;
        public String status;
        public String winner;
        public boolean finishRequested;
        protected MatchRow() { }
        public MatchRow(UUID id,String home,String away,Instant now,Instant close) {
            this.id=id; this.home=home; this.away=away; createdAt=now; closesAt=close; status="OPEN_FOR_BETS";
        }
        public boolean openAt(Instant now) { return status.equals("OPEN_FOR_BETS") && now.isBefore(closesAt); }
    }
    @Entity(name="Bet") @Table(name="bets")
    public static class Bet {
        @Id public UUID id;
        public UUID matchId;
        public UUID userId;
        public String selection;
        @Column(precision=19,scale=2) public BigDecimal stake;
        public Instant placedAt;
        public String status;
        @Column(unique=true) public UUID requestId;
        protected Bet() { }
        public Bet(UUID id,UUID matchId,UUID userId,String selection,BigDecimal stake,Instant now,UUID requestId) {
            this.id=id; this.matchId=matchId; this.userId=userId; this.selection=selection; this.stake=stake; placedAt=now; status="PENDING"; this.requestId=requestId;
        }
    }
    @Entity(name="Settlement") @Table(name="settlements")
    public static class Settlement {
        @Id public UUID betId;
        public UUID userId;
        public String outcome;
        @Column(precision=19,scale=2) public BigDecimal payout;
        public Instant settledAt;
        protected Settlement() { }
        public Settlement(Bet bet,String outcome,BigDecimal payout,Instant now) { betId=bet.id; userId=bet.userId; this.outcome=outcome; this.payout=payout; settledAt=now; }
    }
    @Entity(name="Ledger") @Table(name="ledger")
    public static class Ledger {
        @Id public UUID id;
        public UUID userId;
        public UUID betId;
        @Column(precision=19,scale=2) public BigDecimal amount;
        public String reason;
        public Instant createdAt;
        protected Ledger() { }
        public Ledger(UUID userId,UUID betId,BigDecimal amount,String reason) { id=UUID.randomUUID(); this.userId=userId; this.betId=betId; this.amount=amount; this.reason=reason; createdAt=Instant.now(); }
    }
    @Entity(name="EventRow") @Table(name="event_journal")
    public static class EventRow {
        @Id public UUID id;
        public UUID matchId;
        public long eventOffset;
        @Column(columnDefinition="text") public String payload;
        protected EventRow() { }
        public EventRow(UUID id,UUID matchId,long offset,String payload) { this.id=id; this.matchId=matchId; eventOffset=offset; this.payload=payload; }
    }
    @Entity(name="Outbox") @Table(name="outbox")
    public static class Outbox {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        public String owner;
        public UUID matchId;
        public String destination;
        public String routingKey;
        @Column(columnDefinition="text") public String payload;
        public Instant dueAt;
        public boolean sent;
        protected Outbox() { }
        public Outbox(String owner,UUID matchId,String destination,String routingKey,String payload,Instant dueAt) {
            this.owner=owner; this.matchId=matchId; this.destination=destination; this.routingKey=routingKey; this.payload=payload; this.dueAt=dueAt;
        }
    }
    @Entity(name="Audit") @Table(name="audit")
    public static class Audit {
        @Id public UUID id;
        public String action;
        @Column(columnDefinition="text") public String detail;
        public Instant createdAt;
        protected Audit() { }
        public Audit(String action,String detail) { id=UUID.randomUUID(); this.action=action; this.detail=detail; createdAt=Instant.now(); }
    }
    @Entity(name="Delivered") @Table(name="delivered_notifications")
    public static class Delivered {
        @Id public String id;
        public Instant deliveredAt;
        protected Delivered() { }
        public Delivered(String id) { this.id=id; deliveredAt=Instant.now(); }
    }
}
