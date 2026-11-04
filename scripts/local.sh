#!/usr/bin/env bash
set -euo pipefail
case "${1:-start}" in
 start) cd "$(dirname "$0")/.."; exec npm run dev;;
 stop) systemctl --user stop javac-frontend.service javac-backend.service javac-mysql.service 2>/dev/null || true; printf 'Earlier services stopped. For npm run dev, press Ctrl+C in its terminal.\n';;
 status) curl --noproxy '*' -fsS http://localhost:5173/api/auth/config;;
 *) printf 'Usage: bash scripts/local.sh [start|stop|status]\n' >&2;exit 2;;
esac
