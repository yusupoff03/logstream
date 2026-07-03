package io.github.yusupoff03.logstream.configuration;

import io.github.yusupoff03.logstream.model.LogStreamSession;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final Map<String, LogStreamSession> sessions =
            new ConcurrentHashMap<>();

    public LogStreamSession create(String username) {

        String id = UUID.randomUUID().toString();

        LogStreamSession session =
                new LogStreamSession(
                        id,
                        username,
                        Instant.now(),
                        Instant.now());

        sessions.put(id, session);

        return session;
    }

    public LogStreamSession find(String sessionId) {

        LogStreamSession session = sessions.get(sessionId);

        if (session != null) {
            session.touch();
        }

        return session;
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

}
