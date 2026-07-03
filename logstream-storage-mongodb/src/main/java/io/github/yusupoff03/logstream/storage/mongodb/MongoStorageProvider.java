package io.github.yusupoff03.logstream.storage.mongodb;

import io.github.yusupoff03.logstream.core.LogStorage;
import io.github.yusupoff03.logstream.core.StorageProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;

public class MongoStorageProvider implements StorageProvider {

    @Override
    public int getOrder() {
        return 2;
    }

    @Override
    public boolean supports(ApplicationContext context) {
        return !context.getBeansOfType(MongoTemplate.class).isEmpty();
    }

    @Override
    public LogStorage create(ApplicationContext context) {
        return new MongoLogStorage(context.getBean(MongoTemplate.class));
    }
}
