#!/bin/bash
while true; do
  if ! pgrep -f "node /app/applet/web_server.js" > /dev/null; then
    echo "$(date): Starting FraudShield web_server.js..." >> /var/log/fraudshield_web.log
    node /app/applet/web_server.js >> /var/log/fraudshield_web.log 2>&1
  fi
  sleep 2
done
