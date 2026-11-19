# Ubuntu 26.04 LTS / VirtualBox · versión Node.js

## Crear la VM

Descargar Ubuntu 26.04 LTS desde https://ubuntu.com/download/server y crear una máquina Ubuntu de 64 bits en VirtualBox. Para este laboratorio se recomiendan 4 vCPU, 6–8 GB RAM y 40 GB de disco dinámico. Ubuntu Server reduce el consumo del escritorio.

Instalar Ubuntu, un usuario administrativo y OpenSSH. Con red NAT, configurar en VirtualBox una redirección de **127.0.0.1:2222 del anfitrión al puerto 22 del invitado**. No es necesario abrir Kafka ni RabbitMQ a la red.

## Docker Engine y Compose

Seguir el repositorio oficial de Docker, compatible con Ubuntu 26.04. No mezclar esta instalación con docker.io de Ubuntu si ya está instalado.

```bash
sudo apt update
sudo apt install -y ca-certificates curl git nodejs npm
node --version
                                                                         
                                                                                   
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "$VERSION_CODENAME")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker version
sudo docker compose version
```

Las imágenes incluyen Node.js 22, por lo que Java, Maven y PostgreSQL no se instalan. La instalación Node del host sólo se usa para pruebas; el proyecto también ejecuta las pruebas unitarias durante docker build.

## Copiar y ejecutar

Copiar el repositorio a ~/live-bet-arena con SCP o carpeta compartida.

```bash
cd ~/live-bet-arena
cp .env.example .env
nano .env
chmod 600 .env
npm ci
npm run verify
sudo docker compose config --quiet
sudo docker compose up -d --build --wait --wait-timeout 240
sudo docker compose ps
node scripts/e2e.mjs
```

Completar RABBIT_PASSWORD, DEMO_PASSWORD y ADMIN_PASSWORD. La primera descarga puede tardar varios minutos. Los datos se guardan en volúmenes de Kafka, Zookeeper y RabbitMQ; los servicios Node sólo mantienen proyecciones en memoria.

## Abrir desde el anfitrión

Con la redirección SSH de VirtualBox, ejecutar en el anfitrión:

```bash
ssh -p 2222 -L 8080:127.0.0.1:8080 TU_USUARIO@127.0.0.1
```

Abrir http://127.0.0.1:8080. El túnel transporta HTTP y WebSocket al dashboard local de la VM. Si el puerto del anfitrión está ocupado, usar -L 18080:127.0.0.1:8080 y abrir 18080.

## Demostrar el proyecto

1. Abrir Alex y Sam en ventanas independientes. Cada usuario dispone de puntos virtuales e historial propio.
2. Apostar en un partido abierto y esperar el minuto programado por RabbitMQ.
3. Mostrar el ganador, el retorno o pérdida, los movimientos y los logs de correo simulado.
4. Acceder a administración con admin@arena.test y ADMIN_PASSWORD.
5. Destruir y recrear sólo el motor para demostrar reconstrucción desde Kafka:

```bash
sudo node scripts/recovery-check.mjs
sudo docker compose exec -T results-engine node scripts/duplicate-check.mjs
sudo docker compose exec rabbitmq rabbitmqctl list_exchanges name type
sudo docker compose exec rabbitmq rabbitmqctl list_queues name messages consumers
sudo docker compose exec kafka kafka-topics --bootstrap-server kafka:9092 --describe --topic match_events
```

El script de recuperación necesita permiso para invocar Docker; por eso aquí se ejecuta con sudo. Lee .env desde el directorio del proyecto sin imprimir las contraseñas. Correrlo sin nuevas apuestas simultáneas y después de terminar las existentes.

Mostrar el log de eventos y su clave:

```bash
sudo docker compose exec kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic match_events --from-beginning --property print.key=true
```

Hay nueve servicios de Compose y diez contenedores al ejecutar dos email_worker. dashboard.html es el séptimo componente funcional del enunciado, un cliente HTML servido por dashboard_backend.

## Detener

```bash
sudo docker compose stop
sudo docker compose start
```

No usar down -v para demostrar replay: borraría el log que se quiere conservar. El proceso results_engine no tiene un volumen propio; recrearlo vacía su memoria y obliga a consumir Kafka desde el principio.

La validación registrada en este repositorio se hizo en Ubuntu WSL2. Esta guía documenta VirtualBox, pero no afirma que se haya ejecutado una VM de VirtualBox en esta sesión.

Fuentes: https://docs.docker.com/engine/install/ubuntu/ y https://nodejs.org/en/download.