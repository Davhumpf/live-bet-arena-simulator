# Kafka como fuente única de verdad

Topic `match_events`: una partición, replication factor 1, key `match_id`, retention.ms=-1 y cleanup.policy=delete. No hay compactación ni PostgreSQL en la implementación Node activa.

Cada proceso stateful usa un grupo nuevo al arrancar y fromBeginning=true para reconstruir desde el principio; conserva el estado sólo en memoria. La readiness espera el high watermark capturado al iniciar.

Una partición garantiza el orden global necesario para que las cuentas con apuestas en varios partidos se reconstruyan igual en todas las proyecciones. No aumentar las particiones sin rediseñar el agregado de cuenta.

Los volúmenes del proyecto Compose live-bet-arena-node están aislados de los del despliegue Java anterior.