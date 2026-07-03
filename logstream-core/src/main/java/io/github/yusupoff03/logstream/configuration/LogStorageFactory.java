package io.github.yusupoff03.logstream.configuration;

import io.github.yusupoff03.logstream.core.LogStorage;
import io.github.yusupoff03.logstream.core.impl.DeferredLogStorage;
import io.github.yusupoff03.logstream.core.impl.InMemoryLogStorage;

import java.util.Locale;

/**
 * Factory responsible for selecting and instantiating the initial
 * {@link LogStorage} implementation based on the resolved configuration.
 *
 * <h2>Design rationale</h2>
 * <p>This class exists as a dedicated unit so that
 * {@link LogStreamAutoConfiguration} contains zero selection logic — it simply
 * calls {@link #create(LogStreamProperties.Storage)} and registers the result
 * as a Spring bean.
 *
 * <h2>Two-phase initialization for DATABASE mode</h2>
 * <p>When {@code logstream.storage.type=db} is configured this factory returns
 * a {@link DeferredLogStorage} — not a {@link JdbcLogStorage}
 * directly. The reason is critical for correctness:
 *
 * <ul>
 *   <li>{@code DBLogStorage} depends on {@code JdbcTemplate}, which depends on
 *       {@code DataSource} (HikariPool).  HikariPool is one of the last beans
 *       Spring creates.</li>
 *   <li>If we waited for {@code DBLogStorage} before registering the appender,
 *       every startup log (HikariPool, Flyway, Hibernate, Spring Security,
 *       DispatcherServlet…) would be lost.</li>
 *   <li>{@code DeferredLogStorage} has zero dependencies and can be registered
 *       immediately, allowing the appender to attach to the root Logback logger
 *       in the first wave of bean instantiation.</li>
 * </ul>
 *
 * <p>The {@link LogStreamAutoConfiguration}'s
 * {@link org.springframework.beans.factory.SmartInitializingSingleton} callback
 * later promotes {@code DeferredLogStorage} to the real
 * {@code DBLogStorage} once every singleton bean — including
 * {@code JdbcTemplate} — is available.
 *
 * <h2>Open/Closed Principle</h2>
 * <p>Adding a new back-end (e.g. {@code redis}, {@code file}, {@code mongo})
 * requires only:
 * <ol>
 *   <li>Implementing {@link LogStorage}.</li>
 *   <li>Adding a {@code case} branch in {@link #create}.</li>
 * </ol>
 * No changes are required to {@link LogStreamAutoConfiguration},
 * {@link LogStorage}, {@link io.github.yusupoff03.logstream.core.impl.LogStreamAppender},
 * or {@link io.github.yusupoff03.logstream.controller.LogStreamController}.
 *
 * <h2>Testability</h2>
 * <p>As a {@code final} class with only {@code static} methods this factory is
 * trivially unit-testable without a Spring {@code ApplicationContext}.
 */
public final class LogStorageFactory {

    /** Prevent instantiation — all methods are static. */
    private LogStorageFactory() {}

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Creates and returns the appropriate initial {@link LogStorage}
     * implementation based on {@code config.getType()}.
     *
     * <p>The {@code type} value is trimmed and lower-cased before matching,
     * so {@code "Memory"}, {@code " DB "}, etc. are accepted.
     *
     * <p><strong>Important:</strong> For {@code type=db} the returned object is
     * a {@link DeferredLogStorage}, not a
     * {@link JdbcLogStorage}.  The
     * {@link LogStreamAutoConfiguration}'s
     * {@link org.springframework.beans.factory.SmartInitializingSingleton}
     * callback performs the promotion to the real DB backend after all singletons
     * are instantiated.  Unknown types fail fast here so misconfiguration is
     * surfaced immediately at context startup.
     *
     * @param config the {@code logstream.storage} configuration sub-group;
     *               must not be {@code null}
     * @return the initial {@link LogStorage} instance; never {@code null}
     * @throws IllegalStateException if an unknown type key is supplied
     */
    public static LogStorage create(LogStreamProperties.Storage config) {
        String type = config.getType().trim().toLowerCase(Locale.ROOT);

        return switch (type) {
            case "memory" -> new InMemoryLogStorage();
            case "db"     -> new DeferredLogStorage();
            default       -> throw new IllegalStateException(
                    "Unknown logstream.storage.type='" + config.getType() + "'. " +
                    "Supported values: 'memory', 'db'. " +
                    "Check your application.yml / application.properties."
            );
        };

    }

}
