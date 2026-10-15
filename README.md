# Nexus · Backend

**REST API сервиса знакомств по общим интересам и смыслу личных историй.** Backend отвечает за аккаунты, профиль, закрытые рассказы, галереи, подбор, взаимные симпатии, чаты и контроль доступа.

Java 21 · Spring Boot 3.5.16 · Spring Security · Spring Data JPA · PostgreSQL / H2 · ONNX Runtime CPU

[Документация](https://github.com/SMART-DATING/nexus-docs) · [API](https://github.com/SMART-DATING/nexus-docs/blob/main/architecture/api-contracts.md) · [ERD](https://github.com/SMART-DATING/nexus-docs/blob/main/diagrams/ERD.md) · [Общий запуск](https://github.com/SMART-DATING/nexus-docs/blob/main/RUNBOOK.md)

## Быстрый запуск

Нужны JDK 21 и сеть при первой загрузке Maven/модели. На Windows команды используют PowerShell 7 (`pwsh`); на Linux нужны `curl` и `sha256sum`. Команды выполняются из корня этого репозитория.

Windows, PowerShell 7:

```powershell
pwsh -File scripts/download-model.ps1
.\mvnw.cmd clean package
java -jar target/nexus-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

Linux:

```sh
sh scripts/download-model.sh
sh ./mvnw clean package
java -jar target/nexus-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

API: `http://127.0.0.1:8080/api/v1/health` → `{"status":"ok"}`. Local-профиль хранит H2 в `./data/nexus.mv.db`. Без установленной модели health может работать, но сохранение рассказа вернёт 503.

Этот репозиторий сам не содержит собранный React-интерфейс. Для разработки запустите соседний [nexus-frontend](https://github.com/SMART-DATING/nexus-frontend) на 5173. Для общего сайта на 8088 или сборки единого JAR используйте [RUNBOOK](https://github.com/SMART-DATING/nexus-docs/blob/main/RUNBOOK.md).

## Docker

```sh
docker compose up -d --build --wait
```

Запускаются backend и PostgreSQL 16; API на 127.0.0.1:8080. Данные сохраняются в volume. Только H2-вариант: `docker compose -f compose.h2.yaml up -d --build --wait`. Остановка — `docker compose down` без `-v`. Frontend этими командами не собирается.

## Конфигурация

| Переменная | Назначение |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | local по умолчанию; postgres для внешней БД |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | Параметры БД; local по умолчанию H2 / sa / пустой пароль |
| `NEXUS_MODEL_DIR` | Каталог model.onnx и vocab.txt; по умолчанию `./models/rubert-tiny2` |
| `PORT`, `BIND_ADDRESS` | 8080 / 127.0.0.1 локально; Docker слушает 0.0.0.0 внутри контейнера |

Пути по умолчанию зависят от рабочего каталога. Не открывайте один H2-файл одновременно backend и DBeaver. Обычный запуск создаёт справочник интересов без готовых анкет. Для демонстрации зарегистрируйте тестовые аккаунты и заполните их через интерфейс.

## Основные правила

- Закрытые истории: до 12, каждая 20–6000 символов. Тексты и векторы не выдаются другим участникам.
- Подбор: свой город первым, затем остальные; внутри групп cosine ↓ и userId ↑. Порога сходства нет. Возраст применяется по выбору смотрящего, пол — по выбору обеих сторон.
- Модель локальная; векторы сохраняются. Публичные интересы и город не меняют процент сходства. [Модель и лицензия](MODEL.md).
- Галерея до 6 фото. Чужие раскрываются в пределах числа своих; текущая квота проверяется сервером при каждом чтении.
- Встречные Like создают один match. Чат доступен участникам, блокировка любой стороны закрывает взаимодействие обеим.
- Пауза сохраняет существующие чаты. Личные очистка/скрытие чата не удаляют общую переписку. Собственная выгрузка исключает секреты входа.

## Код и проверки

Рабочий код: `src/main/java/ru/nexus`. Контроллер/DTO определяют HTTP-вход, сервисы — правила, репозитории и сущности — хранение. [Архитектура и UML](https://github.com/SMART-DATING/nexus-docs/blob/main/architecture/system-architecture.md).

После загрузки модели:

```sh
sh ./mvnw verify
```

Windows: `.\mvnw.cmd verify`. PostgreSQL-сценарии требуют TEST_POSTGRES_URL, TEST_POSTGRES_USER и TEST_POSTGRES_PASSWORD отдельной тестовой БД. [План проверок](https://github.com/SMART-DATING/nexus-docs/blob/main/TESTING.md) · [CI](https://github.com/SMART-DATING/nexus-backend/actions/workflows/ci.yml).

## Границы версии

Схема обновляется Hibernate, часть связей проверяется сервисом; фото хранятся в БД, временные разрешения — в памяти процесса. Нет восстановления пароля, удаления аккаунта, жалоб, модерации и rate limit. Текущий статус и задачи постоянного размещения: [STATUS](https://github.com/SMART-DATING/nexus-docs/blob/main/STATUS.md), [roadmap](https://github.com/SMART-DATING/nexus-docs/blob/main/roadmap.md).
