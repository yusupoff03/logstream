package io.github.yusupoff03.logstream.core;

import io.github.yusupoff03.logstream.model.LogEntry;

import java.util.List;
import java.util.Map;

/**
 * Storage abstraction for captured log entries.
 *
 * <p>All components that need to read or write log data must depend on this
 * interface — never on a concrete implementation.  This follows the
 * Dependency Inversion Principle and makes it trivial to swap the backing
 * store (in-memory → PostgreSQL, MySQL, MongoDB, …) without touching any
 * controller, appender, or service.
 *
 * <h2>Adding a new storage backend</h2>
 * <ol>
 *   <li>Create a new Maven module and implement {@link LogStorage}.</li>
 *   <li>Implement {@link StorageProvider} to detect and instantiate the backend.</li>
 *   <li>Register both as beans in a new {@code @AutoConfiguration} class.</li>
 *   <li>No changes to this interface, the appender, or the controllers are required.</li>
 * </ol>
 */
public interface LogStorage {

    /**
     * Persists a single log entry.
     *
     * @param entry the entry to store; must not be {@code null}
     */
    void save(LogEntry entry);

    /**
     * Returns a snapshot of all stored entries in insertion order.
     *
     * @return an immutable copy; never {@code null}
     */
    List<LogEntry> getAll();

    /**
     * Returns the {@code limit} most-recent entries, newest last.
     *
     * @param limit maximum number of entries to return; must be &gt; 0
     * @return a list of at most {@code limit} entries; never {@code null}
     */
    List<LogEntry> getLatest(int limit);

    /**
     * Returns all entries whose {@code id} is strictly greater than
     * {@code afterId} — used for delta-polling by the frontend.
     *
     * <p>Pass {@code -1} to receive every stored entry.
     *
     * @param afterId the last id the caller has already seen, or {@code -1}
     * @return entries newer than {@code afterId}; never {@code null}
     */
    List<LogEntry> findAfter(long afterId);

    /**
     * Returns entry counts grouped by log level.
     * Example: {@code {INFO=42, WARN=3, ERROR=1, DEBUG=120}}
     *
     * @return a map from level name to count; never {@code null}
     */
    Map<String, Long> countByLevel();

    /**
     * Returns the total number of stored entries.
     *
     * @return total count ≥ 0
     */
    long count();

    /**
     * Removes all stored entries, resetting the storage to an empty state.
     */
    void clear();
}
