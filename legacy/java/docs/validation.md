# Validación ejecutada

Fecha: **18 de noviembre de 2026**, zona America/Bogota.

## Resultado

- `mvn -B verify`: **BUILD SUCCESS**, todos los módulos compilados y **27 pruebas** JUnit 5/Mockito, sin fallos, errores ni pruebas omitidas.
- Java del host: Oracle JDK 21.0.11; bytecode objetivo Java 17.
- Las imágenes se construyeron realmente con Maven/JDK 17 y arrancaron con Temurin JRE 17.0.21.
- Docker Engine 29.1.3 y Compose 2.40.3 instalados en la distribución Ubuntu 26.04.1 de WSL2. No se usaron brokers en memoria.
- Once contenedores saludables: seis tipos de microservicio (email-worker tiene dos instancias) y cuatro contenedores de infraestructura.
- `docker compose config --quiet`: configuración válida.

## Flujo distribuido real

`node scripts/e2e.mjs` terminó con **PASS**, entre **12:37:37 y 12:38:39**, hora de Bogotá (aproximadamente 62 segundos).

| Comprobación | Resultado |
| --- | --- |
| HTML, CSS y JavaScript servidos por el dashboard | 200 |
| API privada sin credenciales | 401 |
| Cuenta USER intentando acceder a administración | 403 |
| ADMIN consulta participantes sin recibir hashes | Correcto |
| Generación automática de partido | Correcto |
| Apuesta virtual de 100 puntos | 202, reserva PENDING |
| Repetición de la misma requestId | Misma apuesta, un solo débito |
| Cantidad negativa y saldo insuficiente | 400 |
| Consumo BET_PLACED por results_engine | ACCEPTED |
| Cierre mediante RabbitMQ Delayed Message Exchange | Correcto |
| Resultado aleatorio del partido | HOME |
| Liquidación de la apuesta | WON, retorno 200 puntos |
| Saldo y movimientos únicos del ledger | Correctos |
| Nueva apuesta sobre el partido finalizado | 409 |
| Señales WebSocket recibidas | 13 |

Identificadores de esa ejecución:

```text
matchId: a297f7a3-032b-425b-bb6a-56fe6685d2b6
betId:   b436c244-8844-4533-b6e6-6b076ee06395
```

Informe generado: `artifacts/e2e-report.json`. Los informes son locales y están ignorados por Git; el script los regenera en cada ejecución. `artifacts/unit-tests.json` resume los resultados de Surefire.

## Idempotencia y recuperación

Se ejecutó `bash scripts/check-duplicate.sh`, que volvió a publicar en Kafka un MATCH_FINISHED ya procesado. Resultado:

```text
PASS: MATCH_FINISHED duplicado; liquidaciones y pagos sin cambios (1:200.00)
partido: 6e4b454a-6f13-46e3-9a7a-b607bda2b91a
```

Durante la validación se produjo un apagado automático de WSL. La reserva anterior se conservó en PostgreSQL y se liquidó al recuperar los servicios. Se añadió un proceso de continuidad WSL al wrapper de arranque para evitar esa interrupción al terminar la última sesión. No se modificó la configuración global de WSL. El agregado se reconstruye desde su journal al recibir nuevos eventos; las pruebas cubren también reconstrucción y conservación de una liquidación preexistente.

RabbitMQ mostró `match_timers` como `x-delayed-message`, `notifications` como `topic`, un consumidor en `match_simulation_queue`, dos en `email_notifications` y uno en `dashboard_updates`. El topic Kafka tiene tres particiones y clave por partido. Los detalles se guardan en `artifacts/infrastructure-check.txt`.

## Correcciones encontradas con la integración

La ejecución real permitió corregir el argumento del healthcheck de Zookeeper, un conflicto de nombre de bean Spring Security, el uso de un stream JPA fuera de transacción, la selección de generador aleatorio bajo el cargador del JRE y la incompatibilidad de `mandatory` con el exchange diferido. Las reglas de confirmación AMQP y selección del ganador tienen pruebas de regresión aisladas.

## Alcance y pendiente

Se verificaron el transporte WebSocket, los recursos del frontend, los endpoints, la persistencia, los brokers y el flujo de liquidación. **No se realizó una inspección visual del dashboard en un navegador**: el host de Browser/Computer Use no estuvo disponible en esta sesión. No se afirma haber verificado capturas, layout real ni interacción con clics en navegador. La vista puede abrirse en `http://localhost:8080` para esa revisión.

La guía VirtualBox está entregada; la ejecución distribuida comprobada se hizo en Ubuntu 26.04.1 bajo WSL2, no dentro de VirtualBox. Tampoco se realizaron pruebas de carga, alta disponibilidad o fallos de almacenamiento. Los límites arquitectónicos están descritos en architecture.md.
