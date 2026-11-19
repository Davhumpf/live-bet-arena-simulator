package edu.arena.common;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import java.util.Map;
@Configuration
public class Messaging {
    @Bean RabbitTemplateCustomizer mandatoryRouting() {
                                                                                                        
        return template -> template.setMandatoryExpressionString("messageProperties.headers['x-delay'] == null");
    }
    @Bean NewTopic matchEvents() { return TopicBuilder.name("match_events").partitions(3).replicas(1).config("retention.ms","-1").config("cleanup.policy","delete").build(); }
    @Bean DefaultErrorHandler kafkaErrors() { return new DefaultErrorHandler(new FixedBackOff(1500,FixedBackOff.UNLIMITED_ATTEMPTS)); }
    @Bean Declarables rabbitTopology() {
        var delayed=new CustomExchange("match_timers","x-delayed-message",true,false,Map.of("x-delayed-type","direct"));
        var notifications=new TopicExchange("notifications",true,false);
        var simulations=new Queue("match_simulation_queue",true);
        var email=new Queue("email_notifications",true);
        var dashboard=new Queue("dashboard_updates",true);
        return new Declarables(delayed,notifications,simulations,email,dashboard,
            BindingBuilder.bind(simulations).to(delayed).with("match.finish").noargs(),
            BindingBuilder.bind(email).to(notifications).with("notify.email.*"),
            BindingBuilder.bind(dashboard).to(notifications).with("notify.dashboard.update"));
    }
}
