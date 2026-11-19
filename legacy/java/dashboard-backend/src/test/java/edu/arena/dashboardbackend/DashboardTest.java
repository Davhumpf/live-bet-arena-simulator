package edu.arena.dashboardbackend;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DashboardTest {
    @Test void snapshotNeverExposesPasswordAndUsesAuthenticatedUser() {
        var store=mock(Store.class); var user=new Account(UUID.randomUUID(),"a@arena.test","Alex","secret-hash","USER");
        when(store.byEmail(user.email)).thenReturn(user); when(store.matches()).thenReturn(List.of()); when(store.counts()).thenReturn(Map.of());
        when(store.history(user.id)).thenReturn(List.of()); when(store.ledger(user.id)).thenReturn(List.of());
        var snapshot=(Map<?,?>)new Dashboard(store,"http://localhost:8081").snapshot(()->user.email);
        var safe=(Map<?,?>)snapshot.get("user"); assertFalse(safe.containsKey("password")); assertEquals(user.id,safe.get("id")); verify(store).history(user.id);
    }
}
