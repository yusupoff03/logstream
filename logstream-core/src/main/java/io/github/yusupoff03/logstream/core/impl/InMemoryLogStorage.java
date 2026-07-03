package io.github.yusupoff03.logstream.core.impl;

import io.github.yusupoff03.logstream.core.LogStorage;
import io.github.yusupoff03.logstream.model.LogEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Default {@link LogStorage} implementation backed by an in-memory
 * {@link ArrayList}.
 *
 * <h2>Characteristics</h2>
 * <ul>
 *   <li>Keeps at most {@value #CAPACITY} entries (oldest evicted first).</li>
 *   <li>Thread-safe: all mutating operations synchronize on {@code lock}.</li>
 *   <li>Monotonically increasing {@code id} per entry via {@link AtomicLong}.</li>
 *   <li>{@link #findAfter(long)} scans from the tail — O(delta) for the
 *       typical case where only a few new entries exist since the last poll.</li>
 * </ul>
 *
 * <h2>Replacing this implementation</h2>
 * Create a new class that {@code implements LogStorage}, register it as a
 * Spring bean with higher priority (or set
 * {@code @ConditionalOnMissingBean(LogStorage.class)} on this one in
 * {@link io.github.yusupoff03.logstream.configuration.LogStreamAutoConfiguration}).
 * No other class needs to change.
 */
public class InMemoryLogStorage implements LogStorage {

    /** Maximum number of log entries retained in memory. */
    private static final int CAPACITY = 1_000;

    private final List<LogEntry> store  = new ArrayList<>(CAPACITY + 1);
    private final Object         lock   = new Object();
    private final AtomicLong     idGen  = new AtomicLong(0);

    // ── LogStorage ──────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Assigns the next sequential id before storing.
     * If {@link #CAPACITY} is exceeded the oldest entry is evicted.
     */
    @Override
    public void save(LogEntry entry) {
        // Re-stamp with the internally managed id so the id space is always
        // controlled by this storage layer, not by the caller.
        LogEntry stamped = new LogEntry(
                idGen.getAndIncrement(),
                entry.timestamp(),
                entry.level(),
                entry.logger(),
                entry.thread(),
                entry.message(),
                entry.stacktrace()
        );
        synchronized (lock) {
            store.add(stamped);
            if (store.size() > CAPACITY) {
                store.remove(0);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<LogEntry> getAll() {
        synchronized (lock) {
            return new ArrayList<>(store);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<LogEntry> getLatest(int limit) {
        synchronized (lock) {
            int size  = store.size();
            int start = Math.max(0, size - limit);
            return new ArrayList<>(store.subList(start, size));
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Scans from the tail — efficient when {@code afterId} is close to
     * the most-recently stored id (the common polling case).
     */
    @Override
    public List<LogEntry> findAfter(long afterId) {
        synchronized (lock) {
            if (afterId < 0) {
                return new ArrayList<>(store);
            }
            int split = 0;
            for (int i = store.size() - 1; i >= 0; i--) {
                if (store.get(i).id() <= afterId) {
                    split = i + 1;
                    break;
                }
            }
            return new ArrayList<>(store.subList(split, store.size()));
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Long> countByLevel() {
        synchronized (lock) {
            return store.stream()
                    .collect(Collectors.groupingBy(LogEntry::level, Collectors.counting()));
        }
    }

    /** {@inheritDoc} */
    @Override
    public long count() {
        synchronized (lock) {
            return store.size();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void clear() {
        synchronized (lock) {
            store.clear();
        }
    }
}
