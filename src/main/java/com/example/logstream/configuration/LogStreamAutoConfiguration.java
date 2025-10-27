package com.example.logstream.configuration;

import com.example.logstream.controller.LogStreamController;
import com.example.logstream.core.InMemoryAppender;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LogStreamAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public InMemoryAppender inMemoryAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        InMemoryAppender appender = new InMemoryAppender();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    @Bean
    public LogStreamController logStreamController(InMemoryAppender appender) {
        return new LogStreamController(appender);
    }

}
