#!/usr/bin/env bash
# Today's presentation uses Vite plus an integrated API, not Java/MySQL.
set -euo pipefail
case "${1:-start}" in
 start)
  systemctl --user start javac-frontend.service
  for attempt in {1..60}; do
   if curl --noproxy '*' -fsS http://localhost:5173/api/auth/config >/dev/null 2>&1; then
    printf 'JAVAC is running: http://localhost:5173/\nNo Java, MySQL, OTP or email verification is needed.\n';exit 0
   fi
   sleep 0.5
  done
  printf 'Check: journalctl --user -u javac-frontend -n 60 --no-pager\n' >&2;exit 1;;
 stop) systemctl --user stop javac-frontend.service;printf 'JAVAC stopped. Presentation data is preserved.\n';;
 status) systemctl --user is-active javac-frontend.service;;
 *) printf 'Usage: bash scripts/local.sh [start|stop|status]\n' >&2;exit 2;;
esac
