package io.github.yusupoff03.logstream.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration properties for the LogStream starter.
 *
 * <p>Bound to the {@code logstream} prefix in the application's
 * {@code application.yml} / {@code application.properties}.
 *
 * <h2>Example YAML</h2>
 * <pre>{@code
 * logstream:
 *   storage:
 *     type: memory   # default — no infrastructure required
 * }</pre>
 *
 * <pre>{@code
 * logstream:
 *   storage:
 *     type: db       # requires spring-boot-starter-jdbc on the classpath
 * }</pre>
 *
 * <p>Registered via {@code @EnableConfigurationProperties(LogStreamProperties.class)}
 * in {@link LogStreamAutoConfiguration} — consumers need no extra setup.
 *
 * <p>The nested {@link Storage} class follows the same convention used by
 * Spring Boot's own {@code spring.datasource} and {@code spring.jpa} groups:
 * a dedicated inner class per concern, keeping the root namespace clean and
 * making it straightforward to add sub-keys (e.g.
 * {@code logstream.storage.file.path}) in the future without a breaking change.
 */
@ConfigurationProperties(prefix = "logstream")
public class LogStreamProperties {

    private final Storage storage = new Storage();

    private final Auth auth = new Auth();

    public Storage getStorage() {
        return storage;
    }

    public Auth getAuth() {
        return auth;
    }

    public static class Storage {

        private String type = "memory";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

    }

    public static class Auth {

        private boolean enabled = false;

        private String username;

        private String password;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

    }

}
