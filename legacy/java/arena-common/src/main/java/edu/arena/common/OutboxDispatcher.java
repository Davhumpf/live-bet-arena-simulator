package edu.arena.common;
import edu.arena.common.persistence.Store;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.TimeUnit;
@Service
public class OutboxDispatcher {
    private final Store store; private final KafkaTemplate<String,String> kafka; private final RabbitTemplate rabbit; private final String owner;
    public OutboxDispatcher(Store store,KafkaTemplate<String,String> kafka,RabbitTemplate rabbit,@Value("${spring.application.name}") String owner) {
        this.store=store; this.kafka=kafka; this.rabbit=rabbit; this.owner=owner;
    }
    @Scheduled(fixedDelay=500) @Transactional
    public void dispatch() throws Exception {
        for (var row:store.pending(owner)) {
            if (row.destination.equals("kafka")) {
                kafka.send("match_events",row.matchId.toString(),row.payload).get(15,TimeUnit.SECONDS);
            } else {
                var properties=new MessageProperties();
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                properties.setContentType("application/json");
                properties.setMessageId(row.id.toString());
                if (row.dueAt!=null) properties.setHeader("x-delay",Math.max(1,Duration.between(Instant.now(),row.dueAt).toMillis()));
                var correlation=new CorrelationData(row.id.toString());
                rabbit.send(row.destination.equals("timer") ? "match_timers" : "notifications",row.routingKey,
                    new Message(row.payload.getBytes(StandardCharsets.UTF_8),properties),correlation);
                if (!correlation.getFuture().get(15,TimeUnit.SECONDS).isAck() || correlation.getReturned()!=null)
                    throw new IllegalStateException("RabbitMQ no confirmó el mensaje "+row.id);
            }
            row.sent=true;
        }
    }
}
