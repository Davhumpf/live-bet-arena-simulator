package edu.arena.dashboardbackend;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import java.security.Principal;
import java.util.*;
@RestController
public class Dashboard {
    private final Store store; private final RestClient betting;
    public Dashboard(Store store,@Value("${BETTING_URL:http://betting-api:8080}") String url) { this.store=store; betting=RestClient.builder().baseUrl(url).build(); }
    private Map<String,Object> safe(Account account) {
        return Map.of("id",account.id,"name",account.name,"email",account.email,"balance",account.balance,"role",account.role);
    }
    @GetMapping("/api/snapshot") @Transactional(readOnly=true)
    public Object snapshot(Principal principal) {
        var user=store.byEmail(principal.getName());
        if (user==null) throw new IllegalArgumentException("Usuario desconocido");
        return Map.of("user",safe(user),"matches",store.matches(),"counts",store.counts(),"bets",store.history(user.id),"ledger",store.ledger(user.id),"serverTime",java.time.Instant.now());
    }
    @GetMapping("/api/admin") @Transactional(readOnly=true)
    public Object admin() { return Map.of("users",store.accounts().stream().map(this::safe).toList(),"activity",store.activity()); }
    @PostMapping("/bet")
    public ResponseEntity<String> bet(@RequestHeader("Authorization") String authorization,@RequestBody String payload) {
        try {
            return betting.post().uri("/bet").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().toEntity(String.class);
        } catch (RestClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(e.getResponseBodyAsString());
        } catch (RestClientException e) { return ResponseEntity.status(503).body("{\"error\":\"Servicio de apuestas temporalmente no disponible\"}"); }
    }
}
