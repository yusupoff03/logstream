package io.github.yusupoff03.logstream.core.impl;

import io.github.yusupoff03.logstream.core.LogStorage;
import io.github.yusupoff03.logstream.model.LogEntry;

import java.util.List;
import java.util.Map;

/**
 * Placeholder — File-based {@link LogStorage} implementation.
 *
 * <p>This class is intentionally left unimplemented. To activate file storage:
 * <ol>
 *   <li>Implement all methods of {@link LogStorage}.</li>
 *   <li>Annotate with {@code @Component} and an activation condition such as
 *       {@code @ConditionalOnProperty(name="logstream.storage", havingValue="file")}.</li>
 *   <li>The {@link InMemoryLogStorage} default bean backs off automatically via
 *       {@code @ConditionalOnMissingBean(LogStorage.class)} in the auto-configuration.</li>
 * </ol>
 * No changes to the appender, controller, or auto-configuration are required.
 */
public class FileStorage implements LogStorage {

    @Override public void save(LogEntry entry)             { throw new UnsupportedOperationException("Not implemented"); }
    @Override public List<LogEntry> getAll()               { throw new UnsupportedOperationException("Not implemented"); }
    @Override public List<LogEntry> getLatest(int limit)   { throw new UnsupportedOperationException("Not implemented"); }
    @Override public List<LogEntry> findAfter(long afterId){ throw new UnsupportedOperationException("Not implemented"); }
    @Override public Map<String, Long> countByLevel()      { throw new UnsupportedOperationException("Not implemented"); }
    @Override public long count()                          { throw new UnsupportedOperationException("Not implemented"); }
    @Override public void clear()                          { throw new UnsupportedOperationException("Not implemented"); }

}
