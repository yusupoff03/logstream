package io.github.yusupoff03.logstream.core.impl;

import io.github.yusupoff03.logstream.core.LogStorage;
import io.github.yusupoff03.logstream.model.LogEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A transitional {@link LogStorage} that captures log entries in an
 * in-memory queue from the very first log line and later flushes them
 * to a real backend once it becomes available.
 *
 * <h2>Problem solved</h2>
 * <p>In DATABASE mode, {@link JdbcLogStorage} depends on {@code JdbcTemplate}
 * which depends on {@code DataSource} (HikariPool).  HikariPool is one of the
 * last beans Spring Boot creates, so if the {@link LogStreamAppender} waited
 * for {@link JdbcLogStorage} to be ready, it would miss every startup log
 * (HikariPool, Flyway, Hibernate, Spring Security, DispatcherServlet, …).
 *
 * <p>{@link DeferredLogStorage} has <strong>zero external dependencies</strong>,
 * so it can be created in the very first wave of Spring bean instantiation.
 * The appender attaches to the root Logback logger immediately and every log
 * line — including startup logs — is queued here.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Created at appender startup; all calls hit the in-memory queue.</li>
 *   <li>Once the real backend is ready, the auto-configuration calls
 *       {@link #promoteDelegate(LogStorage)}:
 *       <ul>
 *         <li>The queue is atomically drained to the delegate in insertion order.</li>
 *         <li>All subsequent {@link #save} calls are forwarded to the delegate.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h2>Thread-safety</h2>
 * <ul>
 *   <li>{@code delegate} is {@code volatile} so post-promotion reads are lock-free.</li>
 *   <li>{@link #save} uses double-checked locking: fast path after promotion
 *       (volatile read only), synchronised slow path during buffering.</li>
 *   <li>{@link #promoteDelegate} is {@code synchronized} on {@code this}, so the
 *       drain + delegate assignment is atomic with respect to concurrent
 *       {@link #save} calls — no entries are lost or duplicated.</li>
 * </ul>
 *
 * <p>This class is an internal implementation detail of the LogStream starter
 * and is not part of its public API.
 */
public final class DeferredLogStorage implements LogStorage {

    /**
     * Thread-safe ordered queue that holds entries captured before promotion.
     * {@link ConcurrentLinkedQueue} gives O(1) offer/poll and safe concurrent
     * iteration for the read methods.
     */
    private final Queue<LogEntry> buffer = new ConcurrentLinkedQueue<>();

    /**
     * The real backend installed by {@link #promoteDelegate}.
     * {@code volatile} ensures visibility across threads without requiring a
     * lock on every post-promotion {@link #save} call.
     */
    private volatile LogStorage delegate;

    /**
     * Atomically drains the in-memory buffer to {@code realStorage} and
     * installs it as the live delegate for all future calls.
     *
     * <p>This method is called exactly once by
     * {@link io.github.yusupoff03.logstream.configuration.LogStreamAutoConfiguration}'s
     * {@link org.springframework.beans.factory.SmartInitializingSingleton} hook
     * — i.e., after every singleton bean (including {@code JdbcTemplate} /
     * {@code DataSource}) has been fully constructed.
     *
     * <p>Synchronising on {@code this} ensures that any concurrent
     * {@link #save} call either:
     * <ul>
     *   <li>completes its offer to {@code buffer} before the drain begins, or</li>
     *   <li>sees the non-{@code null} delegate and goes directly there.</li>
     * </ul>
     * Either way, no entry is lost.
     *
     * @param realStorage the actual storage backend to activate; must not be
     *                    {@code null}
     * @throws IllegalArgumentException if {@code realStorage} is {@code null}
     */
    public synchronized void promoteDelegate(LogStorage realStorage) {
        Objects.requireNonNull(realStorage, "realStorage must not be null");

        LogEntry entry;
        while ((entry = buffer.poll()) != null) {
            realStorage.save(entry);
        }

        this.delegate = realStorage;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses double-checked locking:
     * <ol>
     *   <li>Volatile read of {@code delegate} — if non-{@code null} (post-promotion),
     *       forwards directly without acquiring any lock.</li>
     *   <li>If {@code null}, acquires {@code this} to re-check, then either
     *       forwards or buffers atomically.</li>
     * </ol>
     */
    @Override
    public void save(LogEntry entry) {
        LogStorage d = delegate;
        if (d != null) {
            d.save(entry);
            return;
        }

        synchronized (this) {
            d = delegate;
            if (d != null) {
                d.save(entry);
            } else {
                buffer.offer(entry);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the delegate's result if promotion has occurred;
     * otherwise returns a snapshot of the current buffer.
     */
    @Override
    public List<LogEntry> getAll() {
        LogStorage d = delegate;
        return (d != null) ? d.getAll() : new ArrayList<>(buffer);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the delegate's result if promotion has occurred;
     * otherwise slices the tail of the buffer snapshot.
     */
    @Override
    public List<LogEntry> getLatest(int limit) {
        LogStorage d = delegate;
        if (d != null) {
            return d.getLatest(limit);
        }
        List<LogEntry> all = new ArrayList<>(buffer);
        int start = Math.max(0, all.size() - limit);
        return new ArrayList<>(all.subList(start, all.size()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the delegate's result if promotion has occurred;
     * otherwise scans the buffer for entries newer than {@code afterId}.
     */
    @Override
    public List<LogEntry> findAfter(long afterId) {
        LogStorage d = delegate;
        if (d != null) {
            return d.findAfter(afterId);
        }
        if (afterId < 0) {
            return new ArrayList<>(buffer);
        }
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry e : buffer) {
            if (e.id() > afterId) {
                result.add(e);
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Long> countByLevel() {
        LogStorage d = delegate;
        if (d != null) {
            return d.countByLevel();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LogEntry e : buffer) {
            counts.merge(e.level(), 1L, Long::sum);
        }
        return counts;
    }

    /** {@inheritDoc} */
    @Override
    public long count() {
        LogStorage d = delegate;
        return (d != null) ? d.count() : buffer.size();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Before promotion, clears the in-memory buffer.
     * After promotion, clears the delegate storage.
     */
    @Override
    public void clear() {
        LogStorage d = delegate;
        if (d != null) {
            d.clear();
        } else {
            buffer.clear();
        }
    }

}
