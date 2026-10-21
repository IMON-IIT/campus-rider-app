#!/usr/bin/env python3
"""
Simulated rickshaw driver for testing JU Smart Ride with only ONE phone.

Use the phone as the passenger; this script plays the driver:
  1. logs in as a demo driver and goes online at a campus stand,
  2. waits for a ride request (sent to this rickshaw or broadcast to everyone),
  3. accepts it, then "drives" to the pickup sending GPS updates,
  4. starts the trip, drives to the destination and completes it.

Usage:
  python3 scripts/simulate-driver.py                       # driver2 at Bottola
  python3 scripts/simulate-driver.py --driver driver3@juniv.edu --stand shaheed_minar
  python3 scripts/simulate-driver.py --api http://localhost:8090 --once
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.request


def call(api, method, path, token=None, body=None):
    req = urllib.request.Request(api + "/api/v1" + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data=data, timeout=10) as res:
            return json.loads(res.read() or b"{}").get("data")
    except urllib.error.HTTPError as e:
        msg = e.read().decode(errors="replace")
        raise RuntimeError(f"{method} {path} -> {e.code}: {msg}") from None


def drive(api, token, vehicle_id, driver_id, src, dst, steps, delay):
    """Move along the straight line src -> dst, posting a GPS fix at each step."""
    for i in range(1, steps + 1):
        t = i / steps
        lat = src[0] + (dst[0] - src[0]) * t
        lng = src[1] + (dst[1] - src[1]) * t
        call(api, "POST", "/driver/location", token, {
            "driverId": driver_id, "vehicleId": vehicle_id,
            "latitude": lat, "longitude": lng, "locationSource": "SIMULATED",
            "timestamp": int(time.time() * 1000),
        })
        print(f"   📍 GPS {lat:.6f}, {lng:.6f}")
        time.sleep(delay)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--api", default="http://localhost:8080")
    p.add_argument("--driver", default="driver2@juniv.edu")
    p.add_argument("--password", default="password123")
    p.add_argument("--stand", default="bottola", help="campus location id where the rickshaw starts")
    p.add_argument("--steps", type=int, default=6, help="GPS updates per leg")
    p.add_argument("--delay", type=float, default=2.0, help="seconds between GPS updates")
    p.add_argument("--once", action="store_true", help="exit after one completed ride")
    args = p.parse_args()

    auth = call(args.api, "POST", "/auth/login", body={"email": args.driver, "password": args.password})
    token, driver_id = auth["token"], auth["userId"]
    print(f"🛺 Logged in as {auth['fullName']} ({auth.get('rickshawNumber')})")

    locations = {l["id"]: l for l in call(args.api, "GET", "/campus/locations")}
    if args.stand not in locations:
        sys.exit(f"Unknown stand '{args.stand}'. Options: {', '.join(sorted(locations))}")

    vehicle = call(args.api, "POST", "/driver/availability", token, {"isOnline": True, "currentLocationId": args.stand})
    here = (locations[args.stand]["latitude"], locations[args.stand]["longitude"])
    print(f"✅ Online at {locations[args.stand]['name']}. Waiting for ride requests... (Ctrl+C to stop)")

    try:
        while True:
            requests = call(args.api, "GET", "/driver/requests", token) or []
            if not requests:
                time.sleep(3)
                continue

            r = requests[0]
            print(f"\n🔔 Request from {r['passengerName']}: {r['pickupLocationName']} ➔ {r['destinationLocationName']} (৳{r['estimatedFareBdt']:.0f})")
            accepted = call(args.api, "POST", f"/driver/requests/{r['id']}/accept", token)
            print(f"✅ Accepted. Status: {accepted['status']}. Driving to pickup...")

            pickup = (r["pickupLatitude"], r["pickupLongitude"])
            if r.get("passengerLatitude") is not None:
                pickup = (r["passengerLatitude"], r["passengerLongitude"])
            drive(args.api, token, vehicle["id"], driver_id, here, pickup, args.steps, args.delay)

            status = call(args.api, "GET", "/driver/me", token)
            trip = status.get("activeTrip")
            if not trip:
                print("⚠️  Trip disappeared (passenger cancelled?). Back to waiting.")
                here = pickup
                continue

            call(args.api, "POST", f"/driver/trips/{trip['id']}/status", token, {"status": "IN_PROGRESS"})
            print("🚀 Passenger on board, trip started. Driving to destination...")
            dest = (r["destinationLatitude"], r["destinationLongitude"])
            drive(args.api, token, vehicle["id"], driver_id, pickup, dest, args.steps, args.delay)

            call(args.api, "POST", f"/driver/trips/{trip['id']}/status", token,
                 {"status": "COMPLETED", "currentLocationId": r["destinationLocationId"]})
            print("🏁 Trip completed.")
            here = dest
            if args.once:
                break
            print("Waiting for the next request...")
    except KeyboardInterrupt:
        pass
    finally:
        try:
            call(args.api, "POST", "/driver/availability", token, {"isOnline": False})
            print("\n👋 Driver went offline.")
        except Exception:
            pass


if __name__ == "__main__":
    main()
