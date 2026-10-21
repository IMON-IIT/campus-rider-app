package com.jusmartride.driver;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AvailabilityDto {
    @JsonProperty("isOnline")
    @JsonAlias("online")
    private boolean isOnline;
    private String currentLocationId;

    public AvailabilityDto() {
    }

    public AvailabilityDto(boolean isOnline, String currentLocationId) {
        this.isOnline = isOnline;
        this.currentLocationId = currentLocationId;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public String getCurrentLocationId() {
        return currentLocationId;
    }

    public void setCurrentLocationId(String currentLocationId) {
        this.currentLocationId = currentLocationId;
    }
}
