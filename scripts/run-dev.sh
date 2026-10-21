#!/usr/bin/env bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
cd "$DIR"

echo "=== 1. Starting PostgreSQL Database ==="
if ! docker ps | grep -q ju-postgres; then
    docker start ju-postgres || docker run -d --name ju-postgres -e POSTGRES_DB=jusmartride -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16-alpine
fi

echo "=== 2. Setting up ADB Reverse Ports for Connected Android Device ==="
if which adb >/dev/null 2>&1 && adb devices | grep -q 'device$'; then
    adb reverse tcp:8080 tcp:8080 || true
    adb reverse tcp:8081 tcp:8081 || true
    echo "ADB reverse port forwarding configured for port 8080 (backend) & 8081 (metro)."
fi

echo "=== 3. Starting Spring Boot Backend ==="
if ! ss -tuln | grep -q ':8080 '; then
    echo "Launching Spring Boot backend..."
    nohup java -XX:TieredStopAtLevel=1 -jar backend/target/ju-smart-ride-backend-1.0.0-SNAPSHOT.jar > /tmp/ju-backend.log 2>&1 &
    sleep 5
fi

echo "=== 4. Starting Mobile Expo App ==="
cd "$DIR/mobile"
pnpm run start
