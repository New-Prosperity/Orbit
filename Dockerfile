FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache curl

ARG GITHUB_TOKEN

RUN mkdir -p /opt/orbit \
 && curl -sL -H "Authorization: token ${GITHUB_TOKEN}" \
      -H "Accept: application/octet-stream" \
      $(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
        "https://api.github.com/repos/New-Prosperity/Orbit/releases/tags/latest" \
        | sed -n 's/.*"url": "\(https:\/\/api.github.com\/repos\/New-Prosperity\/Orbit\/releases\/assets\/[0-9]*\)".*/\1/p' | head -1) \
      -o /opt/orbit/Orbit.jar

RUN printf '%s\n' \
  '#!/bin/sh' \
  'cd /home/container' \
  'cp /opt/orbit/Orbit.jar Orbit.jar' \
  'mkdir -p data' \
  'exec java -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -jar Orbit.jar' \
  > /opt/orbit/entrypoint.sh \
 && chmod +x /opt/orbit/entrypoint.sh

WORKDIR /home/container
ENTRYPOINT ["/opt/orbit/entrypoint.sh"]
