# Correspondencia con el enunciado 14

| Requisito | Implementación | Evidencia |
| --- | --- | --- |
| Node.js | seis index.mjs independientes, package.json y lockfile | Dockerfiles Node 22 y npm run verify |
| Kafka como fuente única de verdad | shared/domain.mjs y shared/runtime.mjs | recreación del motor con digest financiero idéntico |
| Eventos inmutables y key match_id | match_events, retención ilimitada, sin compactación | inspección Kafka y validación de key al consumir |
| Generar cada 10 s | match-generator/index.mjs | encuentros automáticos en E2E |
| Temporizador real de 60 s | match_timers, x-delayed-message y match_simulation_queue | E2E de aproximadamente 60 s |
| POST /bet con snake_case | betting-api/index.mjs y adaptador del dashboard | contract-report.json |
| Consumidor stateful con Map | Arena.matches y replay desde el principio | recovery-report.json |
| Rechazar después de MATCH_FINISHED | reducer, estado canónico CLOSED | pruebas aisladas y E2E tardía |
| Ganador aleatorio reproducible | hash de identificador aleatorio del partido | pruebas winnerFor |
| Resultados WIN/LOSS | reducer y notifications | dos E2E reales, ganada y perdida |
| Topic Exchange notifications | shared/runtime.mjs | routing keys notify.email.win/loss y notify.dashboard.update |
| Dashboard recibe notify.dashboard.* | dashboard_updates y WebSocket /ws | E2E recibió 11 señales en la última ejecución |
| email_worker imprime notificaciones | dos consumidores de email_notifications | logs de correo simulado |
| Docker Compose | nueve servicios, diez contenedores con la réplica extra | compose ps y healthchecks |

El enunciado enumera dashboard.html como séptimo componente; es el cliente servido por dashboard_backend, no un séptimo proceso Node adicional. El agregado usa CLOSED para el resultado definitivo; el DTO de la interfaz conserva FINISHED como presentación compatible con el frontend existente.