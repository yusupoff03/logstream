package io.github.yusupoff03.logstream.storage.mongodb;

import io.github.yusupoff03.logstream.core.StorageProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

@AutoConfiguration
@ConditionalOnClass(MongoTemplate.class)
public class MongoStorageAutoConfiguration {

    @Bean
    public StorageProvider mongoStorageProvider() {
        return new MongoStorageProvider();
    }
}
