# Validación de la versión Node.js

Fecha local: **18 de noviembre de 2026**, zona America/Bogota. Estos resultados corresponden a Node.js; los resultados anteriores Java están archivados en legacy/java/docs/validation.md.

## Implementación activa

- Seis procesos Node.js, con dos réplicas de email_worker.
- Kafka con Zookeeper y RabbitMQ con Delayed Message Exchange reales.
- Nueve servicios de Compose, **diez contenedores saludables**.
- **Sin PostgreSQL, Java ni Maven en el despliegue activo.**
- Las imágenes se construyeron y ejecutaron con Node.js 22 Alpine.
- Estado del dominio en memoria; recuperación únicamente desde match_events, una partición y match_id como clave.
- `docker compose up -d --build --wait --wait-timeout 240` de la versión final: correcto.

## Pruebas aisladas

**21 pruebas node:test aprobadas**, cero fallos. Comprobadas también durante la construcción de las imágenes. Incluyen creación, cierre CLOSED, aceptación de apuestas, saldo, centésimas enteras, rechazos tardíos y por saldo, ganador reproducible, WIN/LOSS, eventos duplicados, resultado inmutable, replay puro y saldo global entre partidos.

El control sintáctico cubre servicios, biblioteca compartida, scripts y JavaScript del frontend. Informe JUnit local: artifacts/node-unit-tests.xml. No se levantaron brokers para las pruebas unitarias.

## E2E con brokers reales

Se completaron dos ejecuciones de aproximadamente 60 segundos cada una:

| Ejecución | Partido | Resultado de la apuesta |
| --- | --- | --- |
| Inicial Node, 19:30:26–19:31:26 Bogotá | 8728027c-94be-4258-86bf-8c25dfa36773 | WON, ganador HOME |
| Versión final, aproximadamente 19:34–19:35 Bogotá | 8e99c5de-6c77-4345-af33-bbad7bd35bfc | LOST, ganador AWAY |

En ambas se verificaron recursos HTML/CSS/JS, autenticación 401, autorización USER/ADMIN, creación automática, una apuesta de 100 puntos, idempotencia de requestId, saldo exacto, aceptación del motor, cierre real mediante RabbitMQ, liquidación, movimientos únicos, rechazo de apuesta tardía y señal WebSocket.

La última ejecución terminó a las **19:35:55**, con estado **PASS** y **11 señales WebSocket**. Su apuesta fue 4fd0c8be-c8b2-41c1-962d-3b2158eec12f. El informe generado está en artifacts/e2e-report.json.

## Recuperación real del motor

`node scripts/recovery-check.mjs` reemplazó el contenedor results-engine mediante --force-recreate. El nuevo proceso no tenía Maps, caché, journal SQL ni volumen de aplicación.

El motor volvió a leer Kafka desde el comienzo, y se verificó:

```text
PASS
beforeOffset: 61
afterOffset: 65
bets: 2
financialDigest: b33649f44ece628cc233fcd8fc33732ef728c90fdb3690fa3c5bc2f5b057ba2f
```

El digest de usuarios, saldos, apuestas y ledger fue idéntico antes y después. El log del nuevo contenedor confirmó REPLAY_FROM_BEGINNING. Informe: artifacts/recovery-report.json. La comprobación terminó a las 19:36:47 de Bogotá.

## Duplicado real en Kafka

Se volvió a publicar un MATCH_FINISHED ya consumido. El script esperó a que el motor alcanzara el **nuevo offset 72**, de modo que no se limitó a comprobar un estado anterior a la entrega.

El digest financiero permaneció en b33649f44ece628cc233fcd8fc33732ef728c90fdb3690fa3c5bc2f5b057ba2f. Resultado **PASS**, sin repetir créditos ni liquidaciones. Informe de consola: artifacts/node-duplicate-report.txt.

## Contrato del nuevo enunciado

`node scripts/contract-check.mjs` envió una solicitud real con user_id, match_id, bet_on: A, amount y request_id, reutilizando una apuesta conocida para no crear un nuevo débito. La API devolvió la misma apuesta y mantuvo el saldo. Intentar utilizar user_id de otra cuenta devolvió 403. Resultado **PASS**; informe artifacts/contract-report.json.

La E2E queda preparada para realizar también el reintento cruzado entre el contrato camelCase del frontend y el snake_case del enunciado.

## Límites de la comprobación

Los informes de artifacts son locales e ignorados por Git; los scripts los regeneran. Se comprobó la topología en Ubuntu 26.04.1 bajo WSL2, no dentro de VirtualBox. La guía para VirtualBox está actualizada a Node.js.

Se intentó acceder al navegador para inspección visual, pero el host de Browser Use no estuvo disponible. Se verificaron los archivos servidos y el transporte WebSocket, sin afirmar inspección de layout, capturas o clics en navegador.

No se realizaron pruebas de carga ni alta disponibilidad. Las notificaciones son al menos una vez: al hacer replay pueden repetirse correos simulados, mientras los efectos económicos permanecen idempotentes. Los límites de memoria, retención y particionado están explicados en architecture.md.