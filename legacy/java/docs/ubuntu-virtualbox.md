# Ubuntu 26.04 LTS dentro de VirtualBox

## 1. Crear la máquina

Descargar la ISO de Ubuntu 26.04 LTS desde [Ubuntu](https://ubuntu.com/download/server). Crear una VM Linux Ubuntu de 64 bits en VirtualBox, con 4 vCPU, 8 GB de RAM y disco dinámico de al menos 40 GB para este laboratorio. Habilitar virtualización en el anfitrión si VirtualBox lo requiere. Ubuntu Server reduce el consumo de memoria del escritorio.

Instalar Ubuntu, crear un usuario administrativo y activar OpenSSH si se quiere trabajar desde el anfitrión. Una interfaz NAT es suficiente para descargar dependencias. Configurar en VirtualBox una redirección NAT de **127.0.0.1:2222 del anfitrión a 22 del invitado** para SSH. El dashboard no necesita exponerse a la LAN.

## 2. Instalar Docker Engine y Compose

Ubuntu 26.04 figura entre las versiones compatibles en la [documentación oficial de Docker](https://docs.docker.com/engine/install/ubuntu/). Usar un único origen de paquetes. Los siguientes comandos usan el repositorio oficial de Docker; no mezclarlos con `docker.io` de Ubuntu en la misma instalación.

```bash
sudo apt update
sudo apt install -y ca-certificates curl git openjdk-17-jdk maven
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

Los comandos Docker de esta guía usan `sudo`; no requieren añadir usuarios al grupo docker. La compilación Maven ocurre con JDK 17; las imágenes incluyen su propio JDK/JRE. Para E2E instalar además Node.js 22 o superior según [Node.js](https://nodejs.org/en/download) y confirmar `node --version`.

## 3. Copiar y configurar el proyecto

Clonar el repositorio si se ha publicado, o copiar el directorio del proyecto a `~/live-bet-arena` con SCP/carpeta compartida. La aplicación no depende de la ruta del directorio.

```bash
cd ~/live-bet-arena
cp .env.example .env
nano .env
chmod 600 .env
mvn -B verify
sudo docker compose config --quiet
sudo docker compose up -d --build --wait --wait-timeout 300
sudo docker compose ps
```

Reemplazar las cuatro contraseñas por valores diferentes; las cuentas demo requieren al menos 10 caracteres. Las imágenes se construyen desde el contexto raíz, no desde los subdirectorios de servicio. El SQL se aplica al volumen nuevo de PostgreSQL al primer arranque.

## 4. Abrir desde el anfitrión

Con la redirección SSH indicada antes, ejecutar en el anfitrión:

```bash
ssh -p 2222 -L 8080:127.0.0.1:8080 TU_USUARIO@127.0.0.1
```

Abrir `http://127.0.0.1:8080`. El túnel lleva el HTTP y WebSocket al dashboard local de la VM. Si el anfitrión ocupa ese puerto, usar `-L 18080:127.0.0.1:8080` y abrir 18080. No es necesario publicar PostgreSQL ni los brokers.

## 5. Validar y presentar

```bash
node scripts/e2e.mjs
sudo docker compose exec rabbitmq rabbitmq-plugins list -e
sudo docker compose exec rabbitmq rabbitmqctl list_exchanges name type
sudo docker compose exec rabbitmq rabbitmqctl list_queues name messages consumers
sudo docker compose logs --tail=30 email-worker results-engine
```

Mostrar dos ventanas del navegador con Alex y Sam; cada una tiene su saldo y sus apuestas. Apostar en el mismo encuentro, observar la cuenta regresiva y esperar al ganador. La vista Admin requiere la cuenta admin. Para mostrar concurrencia del worker, consultar `docker compose ps email-worker` y los logs con prefijos de ambas réplicas; una notificación individual sólo la procesa una réplica.

La E2E espera al temporizador real y produce `artifacts/e2e-report.json`. No usar el contador visual como prueba única del cierre.

## 6. Detener y conservar

```bash
sudo docker compose stop
sudo docker compose start
```

Los volúmenes conservan cuentas, historial, Kafka y mensajes RabbitMQ. Al reiniciar tras una pausa, los cierres ya vencidos se procesan al recuperar los brokers. No deshabilitar el plugin diferido con mensajes pendientes, porque su [documentación](https://github.com/rabbitmq/rabbitmq-delayed-message-exchange) advierte que esa operación los elimina.
