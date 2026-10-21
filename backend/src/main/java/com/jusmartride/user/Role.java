package com.jusmartride.user;

public enum Role {
    ROLE_PASSENGER,
    ROLE_DRIVER,
    ROLE_ADMIN;

    public static Role fromString(String roleStr) {
        if (roleStr == null) return ROLE_PASSENGER;
        String upper = roleStr.trim().toUpperCase();
        if (upper.equals("PASSENGER") || upper.equals("ROLE_PASSENGER")) return ROLE_PASSENGER;
        if (upper.equals("DRIVER") || upper.equals("ROLE_DRIVER")) return ROLE_DRIVER;
        if (upper.equals("ADMIN") || upper.equals("ROLE_ADMIN")) return ROLE_ADMIN;
        return ROLE_PASSENGER;
    }
}
