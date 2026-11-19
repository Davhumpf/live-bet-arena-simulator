# Seguimiento por fases

1. Arquitectura y estructura: reactor Maven, seis servicios, biblioteca común y contratos definidos.
2. Infraestructura: SQL de PostgreSQL, Kafka/Zookeeper, exchanges/colas y plugin RabbitMQ real.
3. Microservicios: generación, API autenticada, reservas, simulador, motor idempotente y workers implementados.
4. Frontend: dashboard responsive oscuro, selección de equipo, apuestas, historial, movimientos y administración; actualización WebSocket.
5. Contenedores: Dockerfile multietapa por servicio, usuario no root, healthchecks, límites JVM/CPU/RAM, volúmenes y dos workers.
6. Validación: pruebas JUnit/Mockito y script E2E real. Consultar resultados concretos en validation.md.
7. Documentación: README, diagramas, decisiones de consistencia, recuperación y guía Ubuntu 26.04/VirtualBox.

La aplicación no usa servicios de apuestas, pagos ni correo externos. Las pruebas unitarias aíslan dependencias; la E2E requiere los brokers y PostgreSQL reales.
