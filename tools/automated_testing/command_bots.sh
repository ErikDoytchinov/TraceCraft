#!/usr/bin/env bash
# tools/automated_testing/command_bots.sh

SERVER="192.168.178.184" # 77.249.216.173

for i in {0..9}; do
  NAME="headlessmc-bot${i}"
  printf "→ %s… " "$NAME"

  # skip if not running
  if ! docker ps --filter "name=^/${NAME}$" --format '{{.Names}}' \
       | grep -xq "$NAME"; then
    echo "not running"
    continue
  fi

  # send "connect <SERVER><Enter>" into the 'hmc' screen session
   docker exec "$NAME" screen -S hmc -p 0 -X stuff "connect ${SERVER}$(printf '\r')"
#   docker exec "$NAME" screen -S hmc -p 0 -X stuff "click 1 $(printf '\r')"
#   docker exec "$NAME" screen -S hmc -p 0 -X stuff "disconnect $(printf '\r')"
  echo "sent"
done
