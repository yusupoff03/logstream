package io.github.yusupoff03.logstream.model;

import java.time.Instant;

public class LogStreamSession {

    private final String sessionId;

    private final String username;

    private final Instant createdAt;

    private Instant lastAccess;

    public void touch() {
        this.lastAccess = Instant.now();
    }

    public LogStreamSession(String sessionId, String username, Instant createdAt, Instant lastAccess) {
        this.sessionId = sessionId;
        this.username = username;
        this.createdAt = createdAt;
        this.lastAccess = lastAccess;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUsername() {
        return username;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAccess() {
        return lastAccess;
    }

}
