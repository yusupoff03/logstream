package io.github.yusupoff03.logstream.controller;

import io.github.yusupoff03.logstream.core.LogStorage;
import io.github.yusupoff03.logstream.model.LogEntry;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;

/**
 * REST API for LogStream.
 *
 * <pre>
 * GET    /logstream/api/logs            → all captured log entries
 * GET    /logstream/api/logs?afterId=N  → delta: only entries with id > N
 * GET    /logstream/api/stats           → counts by level
 * DELETE /logstream/api/logs            → clear the log buffer
 * </pre>
 *
 * <p>This controller depends exclusively on the {@link LogStorage} abstraction.
 * It has no knowledge of <em>how</em> or <em>where</em> logs are stored.
 * Swapping the storage backend (e.g., from in-memory to PostgreSQL) requires
 * zero changes to this class.
 *
 * <p>The UI page is served as a static resource at {@code /logstream/index.html}.
 * A redirect from {@code /logstream} is registered by
 * {@link io.github.yusupoff03.logstream.configuration.LogStreamAutoConfiguration}.
 */
@RestController
@RequestMapping("/logstream")
public class LogStreamController {

    private static final String GENERATION_ID =
            String.valueOf(ManagementFactory.getRuntimeMXBean().getStartTime());

    private static final String HEADER_GENERATION = "X-LogStream-Generation";

    private final LogStorage storage;

    public LogStreamController(LogStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/api/logs")
    public ResponseEntity<List<LogEntry>> getLogs(
            @RequestParam(required = false, defaultValue = "-1") long afterId,
            HttpServletResponse response
    ) {
        response.setHeader(HEADER_GENERATION, GENERATION_ID);
        return ResponseEntity.ok(storage.findAfter(afterId));
    }

    @GetMapping("/api/stats")
    public ResponseEntity<Map<String, Long>> getStats(HttpServletResponse response) {
        response.setHeader(HEADER_GENERATION, GENERATION_ID);
        return ResponseEntity.ok(storage.countByLevel());
    }

    @DeleteMapping("/api/logs")
    public ResponseEntity<Void> clearLogs() {
        storage.clear();
        return ResponseEntity.noContent().build();
    }

}
