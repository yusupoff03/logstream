package io.github.yusupoff03.logstream.core;

import org.springframework.context.ApplicationContext;

/**
 * Strategy contract for pluggable log-storage backends.
 *
 * <h2>Purpose</h2>
 * <p>Each storage module (JDBC, MongoDB, Redis, …) provides exactly one
 * implementation of this interface and registers it as a Spring bean via its
 * own {@code @AutoConfiguration} class.  The starter's
 * {@code LogStreamAutoConfiguration} collects all registered providers through
 * {@code List<StorageProvider>} injection, sorts them by {@link #getOrder()},
 * and activates the first one whose {@link #supports(ApplicationContext)} check
 * passes.
 *
 * <h2>Adding a new storage backend</h2>
 * <ol>
 *   <li>Create a new Maven module (e.g. {@code logstream-storage-redis}).</li>
 *   <li>Implement {@link StorageProvider} and {@link LogStorage}.</li>
 *   <li>Register the provider as a bean in a new {@code @AutoConfiguration}
 *       class, conditional on the driver being present on the classpath.</li>
 *   <li>Add the module to the starter's {@code pom.xml} as an optional dependency.</li>
 *   <li>Nothing in core or the starter needs to change.</li>
 * </ol>
 *
 * <h2>Ordering</h2>
 * <p>Providers are sorted ascending by {@link #getOrder()} before the first
 * matching one is selected.  Lower values have higher priority.
 * Suggested convention: JDBC=1, MongoDB=2, future providers=3+.
 */
public interface StorageProvider {

    /**
     * Priority used to sort providers when multiple are present on the classpath.
     * Lower value = higher priority.
     *
     * @return the ordering value; must be deterministic
     */
    int getOrder();

    /**
     * Returns {@code true} if this provider can create a usable
     * {@link LogStorage} given the current application context.
     *
     * <p>Typical implementation: check whether the required infrastructure
     * bean (e.g. {@code JdbcTemplate}, {@code MongoTemplate}) is present.
     *
     * @param context the active application context; never {@code null}
     * @return {@code true} if this provider is usable in the given context
     */
    boolean supports(ApplicationContext context);

    /**
     * Creates and returns a fully initialised {@link LogStorage}.
     * This method is called <em>only</em> after {@link #supports} returned
     * {@code true} for the same context.
     *
     * @param context the active application context; never {@code null}
     * @return a ready-to-use storage instance; never {@code null}
     */
    LogStorage create(ApplicationContext context);
}
