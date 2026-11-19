#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose ps
docker compose exec -T rabbitmq rabbitmq-plugins list -e
docker compose exec -T rabbitmq rabbitmqctl list_exchanges name type
docker compose exec -T rabbitmq rabbitmqctl list_queues name messages consumers
docker compose exec -T kafka kafka-topics --bootstrap-server kafka:9092 --describe --topic match_events
