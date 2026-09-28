package dev.turtleroles.policy;

public record PolicyDecision(boolean allowed, String message) {
    public static PolicyDecision allow() {
        return new PolicyDecision(true, "Allowed");
    }

    public static PolicyDecision deny(String message) {
        return new PolicyDecision(false, message);
    }
}
