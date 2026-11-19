#!/usr/bin/env bash
                                                                                 
set -euo pipefail
cd "$(dirname "$0")/.."
event=$(docker compose exec -T postgres psql -U arena -d arena -At -c "SELECT e.match_id::text || '|' || e.payload FROM event_journal e WHERE e.payload::jsonb->>'type'='MATCH_FINISHED' AND EXISTS(SELECT 1 FROM bets b WHERE b.match_id=e.match_id) ORDER BY e.event_offset DESC LIMIT 1")
if [ -z "$event" ]; then echo 'Primero ejecutar una apuesta y esperar su liquidación.' >&2; exit 1; fi
match_id=${event%%|*}
before=$(docker compose exec -T postgres psql -U arena -d arena -At -c "SELECT count(*)::text || ':' || coalesce(sum(payout),0)::text FROM settlements s JOIN bets b ON s.bet_id=b.id WHERE b.match_id='$match_id'")
printf '%s\n' "$event" | docker compose exec -T kafka kafka-console-producer --bootstrap-server kafka:9092 --topic match_events --property parse.key=true --property 'key.separator=|'
sleep 3
after=$(docker compose exec -T postgres psql -U arena -d arena -At -c "SELECT count(*)::text || ':' || coalesce(sum(payout),0)::text FROM settlements s JOIN bets b ON s.bet_id=b.id WHERE b.match_id='$match_id'")
test "$before" = "$after"
echo "PASS: MATCH_FINISHED duplicado; liquidaciones y pagos sin cambios ($after), partido $match_id"
