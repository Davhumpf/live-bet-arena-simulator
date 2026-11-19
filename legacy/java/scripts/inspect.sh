#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose ps
docker compose exec -T rabbitmq rabbitmq-plugins list -e
docker compose exec -T rabbitmq rabbitmqctl list_exchanges name type
docker compose exec -T rabbitmq rabbitmqctl list_queues name messages consumers
docker compose exec -T kafka kafka-topics --bootstrap-server kafka:9092 --describe --topic match_events
docker compose exec -T postgres psql -U arena -d arena -c 'SELECT status,count(*) FROM matches GROUP BY status; SELECT outcome,count(*) FROM settlements GROUP BY outcome; SELECT owner,count(*) FROM outbox WHERE NOT sent GROUP BY owner;'
