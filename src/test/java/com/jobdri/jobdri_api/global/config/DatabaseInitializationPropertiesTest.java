package com.jobdri.jobdri_api.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseInitializationPropertiesTest {

    @Test
    @DisplayName("공통 설정은 JPA 지연 데이터소스 초기화를 비활성화한다")
    void baseYamlDisablesDeferredDatasourceInitialization() {
        Properties properties = loadProperties("application.yaml");

        assertThat(properties.getProperty("spring.jpa.defer-datasource-initialization")).isEqualTo("false");
    }

    @Test
    @DisplayName("dev 설정은 Flyway와 충돌하는 SQL 자동 초기화를 비활성화한다")
    void devYamlDisablesAutomaticSqlInitialization() {
        Properties properties = loadProperties("application-dev.yaml");

        assertThat(properties.getProperty("spring.flyway.enabled")).contains("SPRING_FLYWAY_ENABLED");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(properties.getProperty("spring.jpa.defer-datasource-initialization")).isEqualTo("false");
    }

    @Test
    @DisplayName("prod 설정은 Flyway와 충돌하는 SQL 자동 초기화를 비활성화한다")
    void prodYamlDisablesAutomaticSqlInitialization() {
        Properties properties = loadProperties("application-prod.yaml");

        assertThat(properties.getProperty("spring.flyway.enabled")).contains("SPRING_FLYWAY_ENABLED");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(properties.getProperty("spring.jpa.defer-datasource-initialization")).isEqualTo("false");
    }

    private Properties loadProperties(String classpathResource) {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ClassPathResource(classpathResource));
        Properties properties = factoryBean.getObject();
        return properties == null ? new Properties() : properties;
    }
}
