package com.jobdri.jobdri_api.domain.payment.repository;

import com.jobdri.jobdri_api.domain.payment.entity.PaymentStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class PaymentStatusConstraintTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    @DisplayName("Flyway migration은 PaymentStatus enum 전체 값을 허용하는 CHECK 제약조건을 적용한다")
    void paymentStatusCheckAllowsAllPaymentStatusValuesAfterFlywayMigration() throws Exception {
        createBaselineSchemaWithOldPaymentStatusCheck();

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(Arrays.stream(PaymentStatus.values()).map(Enum::name))
                .containsExactly("PENDING", "PROCESSING", "UNKNOWN", "FAILED", "COMPLETED");
        assertThat(paymentStatusCheckDefinition())
                .contains("PENDING", "PROCESSING", "UNKNOWN", "FAILED", "COMPLETED")
                .doesNotContain("NOT VALID");

        for (PaymentStatus status : PaymentStatus.values()) {
            assertThatCode(() -> insertPayment("order-" + status.name().toLowerCase(), status.name()))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Flyway migration 적용 후 PaymentStatus enum에 없는 값은 DB CHECK 제약조건에서 거절된다")
    void paymentStatusCheckRejectsUnknownDatabaseValueAfterFlywayMigration() throws Exception {
        createBaselineSchemaWithOldPaymentStatusCheck();

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThatThrownBy(() -> insertPayment("order-invalid-status", "CANCELED"))
                .isInstanceOf(SQLException.class);
    }

    private void createBaselineSchemaWithOldPaymentStatusCheck() throws SQLException {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
            statement.execute("""
                    CREATE TABLE mock_applies (
                        id BIGSERIAL PRIMARY KEY
                    )
                    """);
            statement.execute("""
                    CREATE TABLE payments (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        content VARCHAR(255) NOT NULL,
                        order_id VARCHAR(255) NOT NULL UNIQUE,
                        payment_key VARCHAR(255),
                        plan_code VARCHAR(255) NOT NULL,
                        credit_amount INTEGER NOT NULL,
                        price INTEGER NOT NULL,
                        status VARCHAR(255) NOT NULL,
                        approved_at TIMESTAMP,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP,
                        CONSTRAINT payments_status_check CHECK (status IN ('PENDING', 'FAILED', 'COMPLETED'))
                    )
                    """);
            statement.execute("""
                    INSERT INTO payments (
                        user_id,
                        content,
                        order_id,
                        plan_code,
                        credit_amount,
                        price,
                        status,
                        created_at,
                        updated_at
                    )
                    VALUES (1, 'JobDri 크레딧 1회권', 'existing-pending-order', 'ONE_TIME', 1, 2500, 'PENDING', now(), now())
                    """);
        }
    }

    private String paymentStatusCheckDefinition() throws SQLException {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
             var statement = connection.prepareStatement("""
                     SELECT pg_get_constraintdef(oid)
                     FROM pg_constraint
                     WHERE conname = 'payments_status_check'
                     """)) {
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private void insertPayment(String orderId, String status) throws SQLException {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
             var statement = connection.prepareStatement("""
                     INSERT INTO payments (
                         user_id,
                         content,
                         order_id,
                         plan_code,
                         credit_amount,
                         price,
                         status,
                         created_at,
                         updated_at
                     )
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setLong(1, 1L);
            statement.setString(2, "JobDri 크레딧 1회권");
            statement.setString(3, orderId);
            statement.setString(4, "ONE_TIME");
            statement.setInt(5, 1);
            statement.setInt(6, 2500);
            statement.setString(7, status);
            statement.setObject(8, LocalDateTime.now());
            statement.setObject(9, LocalDateTime.now());
            statement.executeUpdate();
        }
    }
}
