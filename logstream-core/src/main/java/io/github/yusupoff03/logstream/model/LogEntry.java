package io.github.yusupoff03.logstream.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a single captured log event with full structured metadata.
 *
 * @param id         monotonically increasing identifier for delta-polling
 * @param timestamp  epoch milliseconds when the event was logged
 * @param level      log level: TRACE, DEBUG, INFO, WARN, ERROR
 * @param logger     fully-qualified logger / class name
 * @param thread     name of the thread that emitted the event
 * @param message    formatted log message
 * @param stacktrace formatted exception stacktrace, or {@code null} if no exception
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogEntry(
        long   id,
        long   timestamp,
        String level,
        String logger,
        String thread,
        String message,
        String stacktrace
) {}
