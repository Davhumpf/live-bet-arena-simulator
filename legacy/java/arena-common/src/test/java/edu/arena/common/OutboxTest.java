package edu.arena.common;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OutboxTest {
    @Test void delayedPluginDisablesMandatoryWhileNotificationsKeepIt() {
        var rabbit=new RabbitTemplate(); new Messaging().mandatoryRouting().customize(rabbit);
        var delayed=new org.springframework.amqp.core.MessageProperties(); delayed.setHeader("x-delay",60000);
        assertFalse(rabbit.isMandatoryFor(new org.springframework.amqp.core.Message(new byte[0],delayed)));
        assertTrue(rabbit.isMandatoryFor(new org.springframework.amqp.core.Message(new byte[0],new org.springframework.amqp.core.MessageProperties())));
    }
    @Test void failedPublishLeavesOutboxPending() {
        var store=mock(Store.class);
        @SuppressWarnings("unchecked") KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);
        var rabbit=mock(RabbitTemplate.class);
        var row=new Outbox("betting-api",UUID.randomUUID(),"kafka","match_events","{}",null);
        when(store.pending("betting-api")).thenReturn(List.of(row));
        when(kafka.send("match_events",row.matchId.toString(),row.payload)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker offline")));
        var dispatcher=new OutboxDispatcher(store,kafka,rabbit,"betting-api");
        assertThrows(Exception.class,dispatcher::dispatch); assertFalse(row.sent); verifyNoInteractions(rabbit);
    }
    @Test void marksSentOnlyAfterBrokerAcknowledges() throws Exception {
        var store=mock(Store.class);
        @SuppressWarnings("unchecked") KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);
        var rabbit=mock(RabbitTemplate.class);
        var row=new Outbox("betting-api",UUID.randomUUID(),"kafka","match_events","{}",null);
        when(store.pending("betting-api")).thenReturn(List.of(row));
        when(kafka.send("match_events",row.matchId.toString(),row.payload)).thenReturn(CompletableFuture.completedFuture(null));
        new OutboxDispatcher(store,kafka,rabbit,"betting-api").dispatch(); assertTrue(row.sent);
    }
}
