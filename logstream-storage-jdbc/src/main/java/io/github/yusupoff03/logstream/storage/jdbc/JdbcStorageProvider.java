package io.github.yusupoff03.logstream.storage.jdbc;

import io.github.yusupoff03.logstream.core.LogStorage;
import io.github.yusupoff03.logstream.core.StorageProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcStorageProvider implements StorageProvider {

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public boolean supports(ApplicationContext context) {
        return !context.getBeansOfType(JdbcTemplate.class).isEmpty();
    }

    @Override
    public LogStorage create(ApplicationContext context) {
        return new JdbcLogStorage(context.getBean(JdbcTemplate.class));
    }
}
