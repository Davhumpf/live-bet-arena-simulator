package edu.arena.matchsimulator;
import org.springframework.stereotype.Component;
import java.util.random.RandomGenerator;
public interface WinnerStrategy {
    String select();
    @Component final class RandomWinner implements WinnerStrategy {
        private final RandomGenerator random;
        public RandomWinner() { this(new java.util.Random()); }
        public RandomWinner(RandomGenerator random) { this.random=random; }
        public String select() { return random.nextBoolean() ? "HOME" : "AWAY"; }
    }
}
