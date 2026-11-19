package edu.arena.emailworker;
import edu.arena.common.*;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.mockito.Mockito.*;
class EmailWorkerTest {
    @Test void duplicateDeliveryIsIgnored() {
        var store=mock(Store.class); var json=new Json(JsonMapper.builder().findAndAddModules().build());
        var user=new Account(UUID.randomUUID(),"a@arena.test","A","hash","USER"); var notification=new Notification("bet:WON",user.id,"WON",BigDecimal.TEN,new BigDecimal("20"));
        when(store.lock(Account.class,user.id)).thenReturn(user);
        var worker=new EmailWorker(store,json); worker.receive(json.write(notification));
        when(store.get(Delivered.class,notification.id())).thenReturn(new Delivered(notification.id())); worker.receive(json.write(notification));
        verify(store,times(1)).save(isA(Delivered.class)); verify(store,times(1)).save(isA(Audit.class));
    }
}
