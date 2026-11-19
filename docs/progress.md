# Migración al nuevo enunciado

- Implementación activa: seis microservicios Node.js, un cliente HTML y mensajería Kafka/RabbitMQ real.
- Java/Spring Boot: preservado en legacy/java, fuera de las imágenes y del despliegue activo.
- PostgreSQL: retirado de Compose. La versión Node no depende de SQL.
- Fuente única de verdad: topic match_events, una partición y match_id como clave.
- Estado: Maps en memoria reconstruidos desde el inicio del log en cada arranque.
- Temporizadores: RabbitMQ Delayed Message Exchange; comandos recuperados al releer los eventos.
- Notificaciones: Topic Exchange notifications con claves notify.email.win, notify.email.loss y notify.dashboard.update.
- Dashboard: conservado, con REST autenticado, apuestas e historial y señales WebSocket.
- Contratos: POST /bet acepta user_id/match_id/bet_on/amount y también los nombres usados por el frontend anterior.
- Pruebas: node:test aislado, E2E real de 60 segundos, prueba de recreación del motor y prueba de duplicado con verificación de offset.

Consultar validation.md para resultados de las ejecuciones de la versión Node. Los informes Java anteriores no se presentan como validación de esta migración.