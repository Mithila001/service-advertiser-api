package mithila001.service_advertiser.config;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import mithila001.service_advertiser.PostgresTestSupport;
import mithila001.service_advertiser.common.exception.GlobalExceptionHandler;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties = {"spring.datasource.hikari.connection-timeout=3000", "spring.datasource.hikari.validation-timeout=1000",
        "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.connection-init-sql=SET statement_timeout = 200"})
class I_DatabaseProtectionTests extends PostgresTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource datasource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.flywaydb.core.Flyway flyway;
    @org.springframework.beans.factory.annotation.Value("${spring.datasource.url}") String databaseUrl;
    @org.springframework.beans.factory.annotation.Value("${spring.datasource.username}") String databaseUser;
    @org.springframework.beans.factory.annotation.Value("${spring.datasource.password}") String databasePassword;
    @Test void statementsCancelAndTransactionsRollback() {
        assertEquals("200ms", jdbc.queryForObject("SHOW statement_timeout", String.class));
        RuntimeException error = assertThrows(RuntimeException.class, () ->
            new TransactionTemplate(transactions).execute(status -> jdbc.queryForObject("SELECT pg_sleep(1)", Object.class)));
        assertEquals(503, new GlobalExceptionHandler().handleUnexpected(error).getStatusCode().value());
        assertEquals(1, jdbc.queryForObject("SELECT 1", Integer.class));
    }
    @Test void poolExhaustionIsSafeIncludingTransactionStart() throws Exception {
        try (var held = datasource.getConnection(); var held2 = datasource.getConnection()) {
            RuntimeException error = assertThrows(RuntimeException.class, () -> jdbc.queryForObject("SELECT 1", Integer.class));
            assertEquals(503, new GlobalExceptionHandler().handleUnexpected(error).getStatusCode().value());
            RuntimeException transactional = assertThrows(RuntimeException.class, () ->
                new TransactionTemplate(transactions).execute(status -> jdbc.queryForObject("SELECT 1", Integer.class)));
            assertEquals(503, new GlobalExceptionHandler().handleUnexpected(transactional).getStatusCode().value());
        }
        assertEquals(1, jdbc.queryForObject("SELECT 1", Integer.class));
    }
    @Test void migrationsUseAnIndependentConnectionAndRemainRepeatable() throws Exception {
        try (var connection = java.sql.DriverManager.getConnection(databaseUrl, databaseUser, databasePassword); var statement = connection.createStatement();
             var result = statement.executeQuery("SHOW statement_timeout")) {
            assertTrue(result.next()); assertEquals("0", result.getString(1));
        }
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }
}
