package io.github.yusupoff03.logstream.autoconfigure;

import ch.qos.logback.classic.Logger;
import io.github.yusupoff03.logstream.controller.AuthController;
import io.github.yusupoff03.logstream.controller.LogStreamController;
import io.github.yusupoff03.logstream.core.LogStorage;
import io.github.yusupoff03.logstream.core.StorageProvider;
import io.github.yusupoff03.logstream.core.impl.DeferredLogStorage;
import io.github.yusupoff03.logstream.core.impl.LogStreamAppender;
import io.github.yusupoff03.logstream.configuration.LogStreamProperties;
import io.github.yusupoff03.logstream.configuration.SessionManager;
import io.github.yusupoff03.logstream.configuration.LogStreamAuthFilter;
import io.github.yusupoff03.logstream.configuration.GlobalExceptionHandler;
import io.github.yusupoff03.logstream.configuration.LogStorageFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Comparator;
import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication
@EnableConfigurationProperties(LogStreamProperties.class)
public class LogStreamAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LogStorage.class)
    public LogStorage logStorage(LogStreamProperties props) {
        return LogStorageFactory.create(props.getStorage());
    }

    @Bean
    public SmartInitializingSingleton logStorageInitializer(
            LogStorage logStorage,
            List<StorageProvider> providers,
            ApplicationContext context) {

        return () -> {
            if (!(logStorage instanceof DeferredLogStorage deferred)) {
                return;
            }

            providers.stream()
                    .sorted(Comparator.comparingInt(StorageProvider::getOrder))
                    .filter(p -> p.supports(context))
                    .findFirst()
                    .map(p -> p.create(context))
                    .ifPresentOrElse(
                            deferred::promoteDelegate,
                            () -> {
                                throw new IllegalStateException(
                                        "logstream.storage.type=db: no supported storage found. " +
                                        "Please add logstream-storage-jdbc or logstream-storage-mongodb to your dependencies."
                                );
                            }
                    );
        };
    }

    @Bean
    @ConditionalOnMissingBean(LogStreamAppender.class)
    public LogStreamAppender logStreamAppender(LogStorage storage) {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        LogStreamAppender appender = new LogStreamAppender(storage);
        appender.setName("LOGSTREAM_APPENDER");
        appender.start();
        rootLogger.addAppender(appender);
        return appender;
    }

    @Bean
    @ConditionalOnMissingBean(LogStreamController.class)
    public LogStreamController logStreamController(LogStorage storage) {
        return new LogStreamController(storage);
    }

    @Bean
    public WebMvcConfigurer logStreamWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addRedirectViewController("/logstream",  "/logstream/index.html");
                registry.addRedirectViewController("/logstream/", "/logstream/index.html");
            }
        };
    }

    @Bean
    public SessionManager sessionManager() {
        return new SessionManager();
    }

    @Bean
    public LogStreamAuthFilter logStreamAuthFilter(
            SessionManager sessionManager,
            LogStreamProperties properties) {
        return new LogStreamAuthFilter(sessionManager, properties);
    }

    @Bean
    public FilterRegistrationBean<LogStreamAuthFilter> logStreamFilter(
            LogStreamAuthFilter filter) {

        FilterRegistrationBean<LogStreamAuthFilter> bean =
                new FilterRegistrationBean<>();

        bean.setFilter(filter);
        bean.addUrlPatterns("/logstream/*");
        bean.setOrder(1);

        return bean;
    }

    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler logStreamExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(AuthController.class)
    public AuthController logStreamAuthController(
            SessionManager sessionManager,
            LogStreamProperties properties) {
        return new AuthController(sessionManager, properties);
    }

}
