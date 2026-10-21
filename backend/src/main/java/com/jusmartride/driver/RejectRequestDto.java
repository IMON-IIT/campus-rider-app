package com.jusmartride.driver;

public class RejectRequestDto {
    private String reason;

    public RejectRequestDto() {
    }

    public RejectRequestDto(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
