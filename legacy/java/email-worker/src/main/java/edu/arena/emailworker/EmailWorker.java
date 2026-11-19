package edu.arena.emailworker;
import edu.arena.common.*;
import edu.arena.common.persistence.*;
import edu.arena.common.persistence.Rows.*;
import org.slf4j.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class EmailWorker {
    private static final Logger log=LoggerFactory.getLogger(EmailWorker.class);
    private final Store store; private final Json json;
    public EmailWorker(Store store,Json json) { this.store=store; this.json=json; }
    @RabbitListener(queues="email_notifications") @Transactional
    public void receive(String payload) {
        var notification=json.read(payload,Notification.class);
                                                                                        
        var user=store.lock(Account.class,notification.userId());
        if (user==null) throw new IllegalStateException("Destinatario inexistente");
        if (store.get(Delivered.class,notification.id())!=null) return;
        String text=switch(notification.outcome()) {
            case "WON" -> "¡FELICIDADES! Retorno de "+notification.payout()+" puntos virtuales (incluye la apuesta).";
            case "REJECTED" -> "Apuesta rechazada por cierre. Reembolso: "+notification.payout()+" puntos.";
            default -> "Esta vez no ganaste. Tu apuesta fue de "+notification.stake()+" puntos.";
        };
        store.save(new Delivered(notification.id()));
        store.save(new Audit("EMAIL_SIMULATED",notification.id()+" · "+text));
        log.info("EMAIL SIMULADO para {} · {} · id={}",user.email,text,notification.id());
    }
}
