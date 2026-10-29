package pl.tokstudiow.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String TEST_JWT_SECRET =
            "dGVzdC1vbmx5LWp3dC1zZWNyZXQtbm90LWZvci1wcm9kdWN0aW9uISE=";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    DynamicPropertyRegistrar testProperties() {
        return registry -> registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
    }

    @Bean
    DatabaseCleaner databaseCleaner(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        return new DatabaseCleaner(jdbc, transactionManager);
    }
}