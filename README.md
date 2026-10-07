# Nexus Backend

Java 21 / Spring Boot 3.5 / JPA / Spring Security. Рабочее API: регистрация, отзываемые Bearer-сессии, профиль-свойства с приватностью, интересы, возрастные фильтры, подбор, like/skip, match, чат, уведомления.

## Локально
```sh
mvn clean package
java -jar target/nexus-backend-0.0.1-SNAPSHOT.jar --nexus.demo=true
```
API: http://localhost:8080/api/v1/health. По умолчанию H2 в `data/`; данные сохраняются. Для UI запустите соседний nexus-frontend на 5173. Java 21 обязательна. Демо: demo@nexus.local и demo1@nexus.local / NexusDemo2026!. Без nexus.demo=true тестовые пользователи не создаются.

## PostgreSQL
`docker compose up --build` поднимает PostgreSQL и backend. Для отдельного Java-процесса задайте DATABASE_URL (JDBC URL), DATABASE_USER, DATABASE_PASSWORD. BIND_ADDRESS по умолчанию 127.0.0.1; контейнер слушает 0.0.0.0. PORT по умолчанию 8080.

## Архитектура и проверки
`controller → service → repository → entity`. Входные DTO — в dto, фильтр аутентификации — security, демо — config. `mvn test` проверяет основной путь и границы доступа на H2. PostgreSQL прогон включается переменными TEST_POSTGRES_URL/USER/PASSWORD, в GitHub Actions настроен отдельный сервис.

Токены непрозрачные, не JWT; в базе только их хеши, срок 24 часа. Пароли BCrypt. Приватные свойства исключаются из публичной проекции. Реакции/создание match — одна транзакция с блокировкой пары в порядке ID.

Полная [документация](https://github.com/SMART-DATING/nexus-docs): RUNBOOK, API, ERD, ограничения и решения. До слияния смотрите ветку feature/working-prototype во всех репозиториях.

Это локальный учебный прототип: без ML, фото, почтового подтверждения, rate limiting и версионированных миграций. Не предназначен для публичного запуска с настоящими персональными данными без следующего этапа доработки.
