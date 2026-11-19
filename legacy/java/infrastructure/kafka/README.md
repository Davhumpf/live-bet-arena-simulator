# Registro de eventos

`match_events`: 3 particiones, clave UUID del partido, retención ilimitada y sin compactación.
La aplicación declara el topic con KafkaAdmin. Una partición conserva el orden de recepción de cada partido.
En esta instalación académica hay un único broker: factor de réplica 1. No ofrece alta disponibilidad.
El journal PostgreSQL es una copia transaccional para reconstrucción rápida; Kafka conserva el registro de transporte.
