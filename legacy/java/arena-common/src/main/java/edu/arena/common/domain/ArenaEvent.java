package edu.arena.common.domain;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record ArenaEvent(UUID eventId, String type, UUID matchId, Instant occurredAt,
    String home, String away, Instant closesAt, UUID betId, UUID userId,
    String selection, BigDecimal stake, String winner) {
    public static ArenaEvent created(UUID id, String home, String away, Instant now, Instant close) {
        return new ArenaEvent(UUID.randomUUID(), "MATCH_CREATED", id, now, home, away, close, null, null, null, null, null);
    }
    public static ArenaEvent bet(UUID id, UUID bet, UUID user, String selection, BigDecimal stake, Instant now) {
        return new ArenaEvent(UUID.randomUUID(), "BET_PLACED", id, now, null, null, null, bet, user, selection, stake, null);
    }
    public static ArenaEvent finished(UUID id, String winner, Instant now) {
        return new ArenaEvent(UUID.randomUUID(), "MATCH_FINISHED", id, now, null, null, null, null, null, null, null, winner);
    }
}
