package edu.arena.common.domain;
import java.math.BigDecimal;
import java.math.RoundingMode;
public interface SettlementPolicy {
    BigDecimal payout(BigDecimal stake, boolean won);
    final class EvenOdds implements SettlementPolicy {
        public BigDecimal payout(BigDecimal stake, boolean won) {
            if (stake == null || stake.signum() <= 0) throw new IllegalArgumentException("Puntos positivos requeridos");
            return won ? stake.multiply(BigDecimal.valueOf(2)).setScale(2, RoundingMode.UNNECESSARY) : BigDecimal.ZERO.setScale(2);
        }
    }
}
