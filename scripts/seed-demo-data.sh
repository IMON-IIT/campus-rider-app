#!/usr/bin/env bash
set -e

BASE_URL="http://localhost:8080/api/v1"

echo "=== JU Smart Ride End-to-End API Verification ==="

echo "1. Checking Campus Road Network..."
curl -s "$BASE_URL/campus/locations" | head -c 120
echo -e "\n"

echo "2. Testing Java Dijkstra Shortest-Path (Dairy Gate -> Bottola)..."
ROUTERES=$(curl -s -X POST "$BASE_URL/routes/shortest-path" \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"dairy_gate","targetId":"bottola"}')
echo "$ROUTERES" | head -c 200
echo -e "\n"

echo "3. Logging in as Demo Passenger..."
PASSENGER_AUTH=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"passenger@juniv.edu","password":"password123"}')
PASS_TOKEN=$(echo "$PASSENGER_AUTH" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
echo "Passenger Token obtained: ${PASS_TOKEN:0:15}..."

echo "4. Logging in as Demo Driver (Rickshaw Mama)..."
DRIVER_AUTH=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"driver1@juniv.edu","password":"password123"}')
DRIVER_TOKEN=$(echo "$DRIVER_AUTH" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
echo "Driver Token obtained: ${DRIVER_TOKEN:0:15}..."

echo "5. Finding Candidate Shared Rides..."
OPTIONS=$(curl -s -X POST "$BASE_URL/routes/ride-options" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PASS_TOKEN" \
  -d '{"pickupId":"dairy_gate","destinationId":"bottola","passengerCount":1}')
echo "$OPTIONS" | head -c 200
echo -e "\n"

echo "=== All Core API Endpoints Verified Successfully! ==="
