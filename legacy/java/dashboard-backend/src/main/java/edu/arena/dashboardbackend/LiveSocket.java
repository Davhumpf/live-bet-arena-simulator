package edu.arena.dashboardbackend;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
@Configuration @EnableWebSocket
public class LiveSocket extends TextWebSocketHandler implements WebSocketConfigurer {
    private final Set<WebSocketSession> sessions=ConcurrentHashMap.newKeySet();
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) { registry.addHandler(this,"/ws"); }
    @Override public void afterConnectionEstablished(WebSocketSession session) { sessions.add(session); }
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) { sessions.remove(session); }
    @Override public void handleTransportError(WebSocketSession session,Throwable error) throws IOException { sessions.remove(session); session.close(); }
    @RabbitListener(queues="dashboard_updates")
    public void update(String ignored) {
                                                                                  
        for (var session:sessions) {
            try { synchronized(session) { if (session.isOpen()) session.sendMessage(new TextMessage("{\"type\":\"UPDATE\"}")); } }
            catch (IOException e) { sessions.remove(session); }
        }
    }
}
