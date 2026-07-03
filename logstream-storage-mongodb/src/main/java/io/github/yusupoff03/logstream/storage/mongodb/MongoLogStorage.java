package io.github.yusupoff03.logstream.storage.mongodb;

import io.github.yusupoff03.logstream.core.LogStorage;
import io.github.yusupoff03.logstream.model.LogEntry;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class MongoLogStorage implements LogStorage {

    private final MongoTemplate mongoTemplate;
    private volatile boolean initialized = false;

    private static final String COLLECTION = "logstream_logs";

    private final AtomicLong idGen = new AtomicLong(0);

    public MongoLogStorage(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }


    @Override
    public void save(LogEntry entry) {
        ensureInitialized();

        LogEntry entryToSave = new LogEntry(
                idGen.getAndIncrement(),
                entry.timestamp(),
                entry.level(),
                entry.logger(),
                entry.thread(),
                entry.message(),
                entry.stacktrace()
        );

        mongoTemplate.insert(entryToSave);
    }

    @Override
    public List<LogEntry> getAll() {
        Query query = new Query()
                .with(Sort.by(Sort.Direction.ASC, "id"));

        return mongoTemplate.find(query, LogEntry.class);
    }

    @Override
    public List<LogEntry> getLatest(int limit) {
        Query query = new Query()
                .with(Sort.by(Sort.Direction.DESC, "id"))
                .limit(limit);

        List<LogEntry> result = mongoTemplate.find(query, LogEntry.class);

        Collections.reverse(result);

        return result;
    }

    @Override
    public List<LogEntry> findAfter(long afterId) {
        Query query = new Query(
                Criteria.where("id").gt(afterId)
        ).with(Sort.by(Sort.Direction.ASC, "id"));

        return mongoTemplate.find(query, LogEntry.class);
    }

    @Override
    public Map<String, Long> countByLevel() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("level")
                        .count()
                        .as("count")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(
                aggregation,
                "logstream_logs",
                Document.class
        );

        Map<String, Long> map = new LinkedHashMap<>();

        for (Document document : results.getMappedResults()) {
            map.put(document.getString("_id"), document.getLong("count"));
        }

        return map;
    }

    @Override
    public long count() {
        return mongoTemplate.count(new Query(), LogEntry.class);
    }

    @Override
    public void clear() {
        mongoTemplate.remove(new Query(), LogEntry.class);
        idGen.set(0);
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }

        synchronized (this) {
            if (initialized) {
                return;
            }

            if (!mongoTemplate.collectionExists(COLLECTION)) {
                mongoTemplate.createCollection(COLLECTION);
            }

            Query query = new Query()
                    .with(Sort.by(Sort.Direction.DESC, "id"))
                    .limit(1);

            LogEntry last = mongoTemplate.findOne(query, LogEntry.class, COLLECTION);

            idGen.set(last == null ? 0L : last.id() + 1);

            initialized = true;
        }
    }

}
