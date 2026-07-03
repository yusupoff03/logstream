package io.github.yusupoff03.logstream.storage.jdbc;

import io.github.yusupoff03.logstream.core.StorageProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
public class JdbcStorageAutoConfiguration {

    @Bean
    public StorageProvider jdbcStorageProvider() {
        return new JdbcStorageProvider();
    }
}
