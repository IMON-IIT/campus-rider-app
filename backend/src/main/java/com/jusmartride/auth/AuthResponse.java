package com.jusmartride.auth;

import com.jusmartride.user.Role;

import java.io.Serializable;

public class AuthResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String token;
    private String userId;
    private String email;
    private String fullName;
    private String phone;
    private Role role;
    private String vehicleId;
    private String rickshawNumber;
    private Integer capacity;
    private Boolean isOnline;

    public AuthResponse() {
    }

    public AuthResponse(String token, String userId, String email, String fullName,
                        String phone, Role role, String vehicleId, String rickshawNumber,
                        Integer capacity, Boolean isOnline) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
        this.vehicleId = vehicleId;
        this.rickshawNumber = rickshawNumber;
        this.capacity = capacity;
        this.isOnline = isOnline;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getRickshawNumber() {
        return rickshawNumber;
    }

    public void setRickshawNumber(String rickshawNumber) {
        this.rickshawNumber = rickshawNumber;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Boolean getIsOnline() {
        return isOnline;
    }

    public void setIsOnline(Boolean online) {
        isOnline = online;
    }
}
