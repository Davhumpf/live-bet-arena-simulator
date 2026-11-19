package edu.arena.common.domain;
import java.time.Instant;
import java.util.*;
public final class Match {
    public enum Status { OPEN_FOR_BETS, CLOSED, FINISHED }
    private sealed interface State permits Open, Closed, Finished {
        Status status();
        default boolean accepts(Instant at, Instant close) { return false; }
    }
    private record Open() implements State {
        public Status status() { return Status.OPEN_FOR_BETS; }
        public boolean accepts(Instant at, Instant close) { return at.isBefore(close); }
    }
    private record Closed() implements State { public Status status() { return Status.CLOSED; } }
    private record Finished() implements State { public Status status() { return Status.FINISHED; } }
    private final MatchId id;
    private final Instant closesAt;
    private State state = new Open();
    private final Map<UUID, ArenaEvent> bets = new LinkedHashMap<>();
    private final Set<UUID> seen = new HashSet<>();
    private String winner;
    public Match(MatchId id, Instant closesAt) { this.id = Objects.requireNonNull(id); this.closesAt = Objects.requireNonNull(closesAt); }
    public boolean apply(ArenaEvent event) {
        if (!id.value().equals(event.matchId())) throw new IllegalArgumentException("Evento de otro partido");
        if (seen.contains(event.eventId())) return false;
        boolean accepted = true;
        switch (event.type()) {
            case "MATCH_CREATED" -> { }
            case "BET_PLACED" -> {
                accepted = state.accepts(event.occurredAt(), closesAt);
                if (accepted) bets.putIfAbsent(event.betId(), event);
            }
            case "MATCH_FINISHED" -> {
                if (event.occurredAt().isBefore(closesAt)) throw new IllegalArgumentException("Cierre prematuro");
                if (!Set.of("HOME", "AWAY").contains(event.winner())) throw new IllegalArgumentException("Ganador inválido");
                if (state instanceof Finished && !Objects.equals(winner, event.winner())) throw new IllegalStateException("Resultado inmutable");
                winner = event.winner(); state = new Finished();
            }
            default -> throw new IllegalArgumentException("Tipo desconocido");
        }
        seen.add(event.eventId()); return accepted;
    }
    public void close(Instant now) { if (state instanceof Open && !now.isBefore(closesAt)) state = new Closed(); }
    public Status status() { return state.status(); }
    public String winner() { return winner; }
    public Collection<ArenaEvent> bets() { return List.copyOf(bets.values()); }
}
