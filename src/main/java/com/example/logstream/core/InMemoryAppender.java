package com.example.logstream.core;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Service
public class InMemoryAppender extends AppenderBase<ILoggingEvent> {

    private final List<String> logs = Collections.synchronizedList(new LinkedList<>());

    @Override
    protected void append(ILoggingEvent eventObject) {
        logs.add(eventObject.getFormattedMessage());
        if (logs.size() > 1000) logs.remove(0);
    }

    public List<String> getLogs() {
        return logs;
    }

}
