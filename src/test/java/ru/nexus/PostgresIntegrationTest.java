package ru.nexus;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = ".+")
@org.springframework.test.context.ActiveProfiles("postgres")
@SpringBootTest(
  properties = {
    "spring.datasource.url=${TEST_POSTGRES_URL}",
    "spring.datasource.username=${TEST_POSTGRES_USER}",
    "spring.datasource.password=${TEST_POSTGRES_PASSWORD}",
    "nexus.demo=false",
  }
)
class PostgresIntegrationTest extends ApiIntegrationTest {}
