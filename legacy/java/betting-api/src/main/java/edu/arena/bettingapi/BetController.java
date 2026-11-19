package edu.arena.bettingapi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;
import java.math.BigDecimal;
@RestController
public class BetController {
    public record Request(@NotNull UUID matchId,@NotNull @Pattern(regexp="HOME|AWAY") String selection,
        @NotNull @DecimalMin("0.01") @Digits(integer=7,fraction=2) BigDecimal stake,@NotNull UUID requestId) { }
    private final BettingService service;
    public BetController(BettingService service) { this.service=service; }
    @PostMapping("/bet")
    public ResponseEntity<?> bet(Principal principal,@Valid @RequestBody Request request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.place(principal.getName(),request.matchId(),request.selection(),request.stake(),request.requestId()));
    }
}
