-- V2: GPS-based pickup pins and per-driver rejection of broadcast ride requests

ALTER TABLE ride_requests ADD COLUMN IF NOT EXISTS passenger_latitude DOUBLE PRECISION;
ALTER TABLE ride_requests ADD COLUMN IF NOT EXISTS passenger_longitude DOUBLE PRECISION;

-- A broadcast request (no targeted driver) stays REQUESTED when one driver declines it;
-- that driver is recorded here so the request is hidden from them only.
CREATE TABLE IF NOT EXISTS ride_request_rejections (
    request_id VARCHAR(36) NOT NULL REFERENCES ride_requests(id) ON DELETE CASCADE,
    driver_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (request_id, driver_id)
);
