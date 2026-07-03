package io.github.yusupoff03.logstream.core.impl;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import io.github.yusupoff03.logstream.core.LogStorage;
import io.github.yusupoff03.logstream.model.LogEntry;

/**
 * Logback {@link AppenderBase} adapter for LogStream.
 *
 * <h2>Single responsibility</h2>
 * This class is responsible for <em>one thing only</em>: converting a
 * Logback {@link ILoggingEvent} into a {@link LogEntry} and handing it off
 * to the injected {@link LogStorage}.  It owns <strong>no collections, no
 * id generator, and no query methods</strong>.  All storage concerns live
 * exclusively in the {@link LogStorage} implementation.
 *
 * <h2>Lifecycle</h2>
 * Instantiated and started by
 * {@link io.github.yusupoff03.logstream.configuration.LogStreamAutoConfiguration},
 * which also attaches it to the root Logback logger.
 */
public class LogStreamAppender extends AppenderBase<ILoggingEvent> {

    /** Maximum stacktrace frames included per exception. */
    private static final int MAX_FRAMES = 30;

    private final LogStorage storage;

    /**
     * @param storage the storage backend to delegate all persistence to;
     *                injected via Spring constructor injection
     */
    public LogStreamAppender(LogStorage storage) {
        this.storage = storage;
    }

    // ── Logback callback ────────────────────────────────────────────────────────

    /**
     * Converts the incoming Logback event to a {@link LogEntry} and saves it.
     *
     * <p>The {@code id} field is intentionally left as {@code 0} here; the
     * storage layer is responsible for assigning the final sequential id
     * (see {@link InMemoryLogStorage#save(LogEntry)}).
     */
    @Override
    protected void append(ILoggingEvent event) {
        LogEntry entry = new LogEntry(
                0L,                                       // id assigned by storage
                event.getTimeStamp(),
                event.getLevel().toString(),
                event.getLoggerName(),
                event.getThreadName(),
                event.getFormattedMessage(),
                formatThrowable(event.getThrowableProxy())
        );
        storage.save(entry);
    }

    // ── Internal helpers ────────────────────────────────────────────────────────

    private String formatThrowable(IThrowableProxy proxy) {
        if (proxy == null) return null;
        StringBuilder sb = new StringBuilder();
        appendProxy(sb, proxy);
        return sb.toString();
    }

    private void appendProxy(StringBuilder sb, IThrowableProxy proxy) {
        sb.append(proxy.getClassName()).append(": ").append(proxy.getMessage()).append('\n');

        StackTraceElementProxy[] frames = proxy.getStackTraceElementProxyArray();
        if (frames != null) {
            int limit = Math.min(frames.length, MAX_FRAMES);
            for (int i = 0; i < limit; i++) {
                sb.append("\tat ").append(frames[i].getSTEAsString()).append('\n');
            }
            if (frames.length > MAX_FRAMES) {
                sb.append("\t... ").append(frames.length - MAX_FRAMES).append(" more\n");
            }
        }

        if (proxy.getCause() != null) {
            sb.append("Caused by: ");
            appendProxy(sb, proxy.getCause());
        }
    }
}
