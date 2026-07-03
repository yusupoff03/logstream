package io.github.yusupoff03.logstream.storage.jdbc;

import io.github.yusupoff03.logstream.core.LogStorage;
import io.github.yusupoff03.logstream.model.LogEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JDBC-backed {@link LogStorage} implementation.
 *
 * <p>Persists log entries to a relational database via Spring's
 * {@link JdbcTemplate}.  The table is created automatically on the first
 * {@link #save} call if it does not already exist.
 *
 * <h2>ID assignment</h2>
 * <p>{@link io.github.yusupoff03.logstream.core.impl.LogStreamAppender} always
 * creates {@link LogEntry} objects with {@code id = 0} — the storage layer is
 * responsible for assigning the final, unique, monotonically increasing id.
 * This class maintains its own {@link AtomicLong} generator, seeded at
 * construction from {@code MAX(id)} already present in the table, so that:
 * <ul>
 *   <li>No duplicate primary-key violations occur within a single run.</li>
 *   <li>No collisions occur with ids written during previous application runs.</li>
 * </ul>
 *
 * <h2>Table schema</h2>
 * <pre>{@code
 * CREATE TABLE IF NOT EXISTS logstream_logs (
 *     id         BIGINT PRIMARY KEY,
 *     timestamp  BIGINT NOT NULL,
 *     level      VARCHAR(20),
 *     logger     VARCHAR(500),
 *     thread     VARCHAR(255),
 *     message    TEXT,
 *     stacktrace TEXT
 * );
 * }</pre>
 *
 * <h2>Activation</h2>
 * <p>This class is instantiated by
 * {@link io.github.yusupoff03.logstream.configuration.LogStreamAutoConfiguration}
 * when {@code logstream.storage.type=db} is set and a {@code JdbcTemplate} bean
 * is present on the classpath.
 */
public class JdbcLogStorage implements LogStorage {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS logstream_logs (
                id         BIGINT PRIMARY KEY,
                timestamp  BIGINT NOT NULL,
                level      VARCHAR(20),
                logger     VARCHAR(500),
                thread     VARCHAR(255),
                message    TEXT,
                stacktrace TEXT
            )
            """;

    private static final String MAX_ID = """
            SELECT COALESCE(MAX(id) + 1, 0)
            FROM logstream_logs
            """;

    private static final String INSERT = """
            INSERT INTO logstream_logs
            (id, timestamp, level, logger, thread, message, stacktrace)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String FIND_ALL = """
            SELECT *
            FROM logstream_logs
            ORDER BY id
            """;

    private static final String FIND_AFTER = """
            SELECT *
            FROM logstream_logs
            WHERE id > ?
            ORDER BY id
            """;

    private static final String FIND_LATEST = """
            SELECT *
            FROM (
                SELECT *
                FROM logstream_logs
                ORDER BY id DESC
                LIMIT ?
            ) t
            ORDER BY id
            """;

    private static final String COUNT = """
            SELECT COUNT(*)
            FROM logstream_logs
            """;

    private static final String CLEAR = """
            DELETE FROM logstream_logs
            """;

    private static final String COUNT_BY_LEVEL = """
            SELECT level, COUNT(*)
            FROM logstream_logs
            GROUP BY level
            """;

    private static final RowMapper<LogEntry> ROW_MAPPER =
            (rs, rowNum) -> new LogEntry(
                    rs.getLong("id"),
                    rs.getLong("timestamp"),
                    rs.getString("level"),
                    rs.getString("logger"),
                    rs.getString("thread"),
                    rs.getString("message"),
                    rs.getString("stacktrace")
            );

    // ── State ──────────────────────────────────────────────────────────────────

    private final JdbcTemplate jdbcTemplate;

    /**
     * Monotonically increasing id generator.
     *
     * <p>Seeded from {@code MAX(id) + 1} in the existing table so that ids
     * are always unique, even across application restarts.  Initialized in
     * {@link #initDatabase()}, which is called once on the first {@link #save}.
     */
    private final AtomicLong idGen = new AtomicLong(0);

    /**
     * Ensures {@link #initDatabase()} runs exactly once even under concurrent
     * {@link #save} calls.
     */
    private volatile boolean initialized = false;

    /**
     * Creates a new {@link JdbcLogStorage}.
     *
     * <p>The table is created and the id generator is seeded lazily on the
     * first {@link #save} call — not here — so that the constructor itself
     * is lightweight and never blocks.
     *
     * @param jdbcTemplate the JDBC template to use; must not be {@code null}
     */
    public JdbcLogStorage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Re-stamps {@code entry} with a new sequential id before persisting
     * to avoid the duplicate primary-key violation that would result from the
     * {@code id = 0} placeholder created by
     * {@link LogStreamAppender#append(ch.qos.logback.classic.spi.ILoggingEvent)}.
     */
    @Override
    public void save(LogEntry entry) {
        ensureInitialized();

        LogEntry stamped = new LogEntry(
                idGen.getAndIncrement(),
                entry.timestamp(),
                entry.level(),
                entry.logger(),
                entry.thread(),
                entry.message(),
                entry.stacktrace()
        );

        jdbcTemplate.update(
                INSERT,
                stamped.id(),
                stamped.timestamp(),
                stamped.level(),
                stamped.logger(),
                stamped.thread(),
                stamped.message(),
                stamped.stacktrace()
        );
    }

    /** {@inheritDoc} */
    @Override
    public List<LogEntry> getAll() {
        return jdbcTemplate.query(FIND_ALL, ROW_MAPPER);
    }

    /** {@inheritDoc} */
    @Override
    public List<LogEntry> getLatest(int limit) {
        return jdbcTemplate.query(FIND_LATEST, ROW_MAPPER, limit);
    }

    /** {@inheritDoc} */
    @Override
    public List<LogEntry> findAfter(long afterId) {
        return jdbcTemplate.query(FIND_AFTER, ROW_MAPPER, afterId);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Long> countByLevel() {
        return jdbcTemplate.query(COUNT_BY_LEVEL, rs -> {
            Map<String, Long> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getString(1), rs.getLong(2));
            }
            return result;
        });
    }

    /** {@inheritDoc} */
    @Override
    public long count() {
        Long result = jdbcTemplate.queryForObject(COUNT, Long.class);
        return result != null ? result : 0L;
    }

    /** {@inheritDoc} */
    @Override
    public void clear() {
        jdbcTemplate.update(CLEAR);
    }

    /**
     * Creates the table (if absent) and seeds the id generator from the
     * current maximum id in the table.
     *
     * <p>Uses a double-checked locking pattern so that the DDL and the
     * {@code MAX(id)} query are executed at most once across concurrent
     * {@link #save} calls.
     */
    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            jdbcTemplate.execute(CREATE_TABLE);

            Long nextId = jdbcTemplate.queryForObject(MAX_ID, Long.class);
            idGen.set(nextId != null ? nextId : 0L);

            initialized = true;
        }
    }

}
