#!/usr/bin/env bash
set -euo pipefail

N=10

for i in $(seq 0 $((N-1))); do
  NAME=headlessmc-bot${i}
  echo "Starting container $NAME …"

  docker run -d -it \
    --name "$NAME" \
    -v "$PWD"/tools/automated_testing/bot${i}/config/config.properties:/headlessmc/HeadlessMC/config.properties \
    -v "$PWD"/tools/automated_testing/bot${i}/run:/headlessmc/HeadlessMC/run \
    hmc-launcher

  echo " → $NAME up"
done

echo "All $N bots started."
