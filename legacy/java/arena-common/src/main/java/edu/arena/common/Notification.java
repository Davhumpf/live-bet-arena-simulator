package edu.arena.common;
import java.math.BigDecimal;
import java.util.UUID;
public record Notification(String id, UUID userId, String outcome, BigDecimal stake, BigDecimal payout) { }
