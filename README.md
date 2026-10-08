# Nexus Backend

Сервер сервиса знакомств SMART-DATING: регистрация, сессии, профиль с настройкой видимости, интересы, подбор, взаимные симпатии, чат и уведомления.

**Стек:** Java 21, Spring Boot 3.5.16, Spring Security, Spring Data JPA/Hibernate, PostgreSQL 16. Для запуска без Docker есть файловая H2.

## 1. Получить рабочую версию
```sh
git clone --branch feature/working-prototype https://github.com/SMART-DATING/nexus-backend.git
cd nexus-backend
```
Готовое приложение находится в `feature/working-prototype`. В `main` пока исходная заготовка. Изменения можно просмотреть в PR #12.

## 2. Запуск без Docker: H2
Установите JDK 21 и задайте JAVA_HOME на его каталог. Отдельно устанавливать Maven не нужно: в репозитории есть Maven Wrapper.

Windows PowerShell:
```powershell
.\mvnw.cmd clean package
java -jar target/nexus-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --nexus.demo=true
```
Linux/macOS:
```sh
./mvnw clean package
java -jar target/nexus-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --nexus.demo=true
```
Проверка готовности: http://127.0.0.1:8080/api/v1/health → `{"status":"ok"}`. Дождитесь завершения запуска. H2 автоматически создаёт `data/nexus.mv.db` относительно текущего каталога. Сервис H2 отдельно включать не требуется. Консоль H2 отключена: это не мешает работе API. Для одного файла запускайте одну копию приложения.

Для интерфейса запустите соседний nexus-frontend на 5173. Backend без собранного frontend на `/` может вернуть 404 — проверяйте `/api/v1/health`.

## 3. Запуск с PostgreSQL
Требуется работающий Docker Engine/Desktop с Linux-контейнерами.
```sh
docker compose up --build --wait
```
Команда поднимает PostgreSQL и backend; API доступен на http://127.0.0.1:8080. Демо по умолчанию выключено. В PowerShell включите его перед командой: `$env:NEXUS_DEMO='true'`; в Linux: `NEXUS_DEMO=true docker compose up --build --wait`.

Если нужен только PostgreSQL для запуска Java из IDE:
```sh
docker compose up -d postgres
java -jar target/nexus-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=postgres --nexus.demo=true
```
Локальные значения по умолчанию: БД `nexus`, пользователь `nexus`, пароль `nexus-local-dev`, порт 5432. В IDE выберите JDK 21, главный класс `ru.nexus.NexusApplication` и профиль `postgres` или `local`.

Полный стек с интерфейсом запускается командой `docker compose up --build --wait` из соседнего каталога nexus-frontend, интерфейс будет на http://127.0.0.1:8088.

## 4. H2 внутри контейнера
```sh
docker compose -f compose.h2.yaml up --build --wait
```
Этот вариант не запускает PostgreSQL. Данные H2 сохраняются в volume `nexus-h2`, API на 8080, демо включено. Остановка соответствующего варианта: `docker compose down` или `docker compose -f compose.h2.yaml down`. Не добавляйте `-v`, если хотите сохранить данные.

## 5. Настройки
| Переменная | Назначение |
|---|---|
| SPRING_PROFILES_ACTIVE | `local` (по умолчанию, H2) или `postgres` |
| DATABASE_URL | JDBC URL выбранного типа базы |
| DATABASE_USER / DATABASE_PASSWORD | Учётные данные БД |
| NEXUS_DEMO | `true` создаёт 14 вымышленных анкет с иллюстрированными аватарами |
| PORT | Порт API, по умолчанию 8080 |
| BIND_ADDRESS | По умолчанию 127.0.0.1, в Docker 0.0.0.0 |

Не смешивайте URL PostgreSQL с профилем `local`: у профилей разные драйверы. Backend в контейнере соединяется с БД по имени `postgres`, а не localhost. Порты контейнеров публикуются только на loopback.

## 6. Демо и основные запросы
Аккаунты `demo@nexus.local`, `demo1@nexus.local` … `demo13@nexus.local`, пароль `NexusDemo2026!`. В первой вкладке войдите как demo, во второй — demo1; поставьте встречные like и откройте чат в «Совпадениях». Автоответов нет. Анкеты имеют разные описания и интересы; `avatarUrl` указывает на локальный SVG-портрет, который раздаёт frontend. Новые обычные аккаунты могут не иметь `avatarUrl` до загрузки фотографии.

API имеет префикс `/api/v1`. `POST /auth/register` и `/auth/login` принимают `{"email":"...","password":"..."}` и возвращают `accessToken`. Остальные запросы требуют `Authorization: Bearer <accessToken>`. Токен живёт 24 часа, logout отзывает его.

| Метод | Путь | Назначение |
|---|---|---|
| GET | /users/me | Аккаунт, профиль и предпочтения |
| GET / PUT | /profiles/me | Прочитать / сохранить свойства и интересы |
| GET | /interests | Справочник названий интересов |
| GET / PUT | /preferences/me | minAge и maxAge |
| GET | /recommendations | items с commonInterests и compatibilityScore, skippedCount |
| POST | /recommendations/next | Следующая подборка; автоматически возвращает Skip после окончания непросмотренных анкет |
| POST | /recommendations/restart | Вернуть пропущенные анкеты, ответ `{"restored": число}` |
| POST | /users/{id}/like или /skip | Реакция |
| GET | /matches и /matches/{id} | Взаимные совпадения |
| GET / POST | /matches/{id}/messages | История / отправка текста |
| GET | /notifications | Уведомления |
| PATCH | /notifications/{id}/read | Прочтение уведомления |
| POST | /auth/logout | Выход, ответ 204 |

Пример PUT /profiles/me:
```json
{"properties":[{"name":"display_name","value":"Алекс","visible":true},{"name":"bio","value":"Люблю музыку","visible":true},{"name":"birth_date","value":"2001-04-12","visible":false},{"name":"city","value":"Москва","visible":true}],"interests":["Музыка","Кофе"]}
```
Имя и город обязательны; возраст 18–100; 1–10 интересов из справочника. Неизвестные интересы возвращают 400. Публичный профиль не содержит скрытых свойств. Сообщения доступны только участникам match, текст до 5000 символов. Ошибки содержат `status` и `message`.

Клиент получает круговую подборку через `POST /recommendations/next?limit=20`: сначала непросмотренные подходящие анкеты, затем собственные Skip. Ответ содержит items, skippedCount и cycleRestarted. Like никогда не сбрасываются; если все подходящие анкеты лайкнуты или не подходят по возрасту, выдача пуста. GET /recommendations остаётся запросом без изменений истории. Для совместимости можно явно вернуть пропущенные через `POST /recommendations/restart` без тела запроса. Операция удаляет только собственные реакции skip. Лайки, встречные реакции, совпадения и переписки сохраняются. Повторный вызов без новых пропусков возвращает `{"restored":0}`. Возрастные предпочтения продолжают действовать, поэтому число возвращённых в подборку анкет может быть меньше restored. Автоматический новый круг начинается через POST /recommendations/next, включая загрузку раздела знакомств после обновления страницы.

## 7. Структура и данные
`controller → service → repository → entity`; входные DTO в dto, аутентификация в security, заполнение данных в config. `ru.nexus.Interests` содержит исходные названия справочника, `entity.Interest` — сущность базы. Эти классы включены в репозиторий.

10 сущностей: UserAccount, ProfileProperty, Interest, UserInterest, Preference, Reaction, PairMatch, ChatMessage, Notice, SessionToken. Рекомендации рассчитываются по доле общих интересов. При встречных like строки пользователей блокируются в порядке ID, match создаётся один раз.

При открытии базы старой версии CatalogueData переносит интересы и предпочтения в новые таблицы; старые таблицы/колонки остаются для сохранности исходных данных. DemoData исправляет недозаполненные демоанкеты, не перезаписывая заполненные, и добавляет отсутствующий avatarKey только демоаккаунтам. Автоматические миграции полной схемы пока заменены Hibernate `ddl-auto=update` — это ограничение учебного прототипа.

## Фото профиля

`POST /api/v1/profiles/me/avatar`: Bearer и multipart/form-data с полем file. Один JPEG/PNG до 5 МБ, до 25 мегапикселей; содержимое проверяется декодером, уменьшается до 1200 px по длинной стороне и сохраняется как JPEG без исходных метаданных. Ответ — профиль с avatarUrl. DELETE по тому же адресу убирает собственное фото (у демоаккаунта возвращается SVG). GET /api/v1/avatars/{userId}?v={version} публично возвращает JPEG или 404. Фото — публичная часть анкеты. Байты хранятся в users.avatar_image (bytea, совместимо с H2 PostgreSQL mode); avatar_version меняется при замене. Фото сохраняются вместе с БД, отдельный файловый volume не нужен. Ошибка размера — 413, формата — 400.

## 8. Проверки
`./mvnw test` / `.\mvnw.cmd test`: сценарии API и контроля доступа, конкурирующие like, возврат собственных skip с сохранением переписок, запуск 14 демоанкет, сохранение отредактированных анкет при повторном запуске, перенос старых данных и реальный HTTP-вход. PostgreSQL-тест включается переменными TEST_POSTGRES_URL, TEST_POSTGRES_USER, TEST_POSTGRES_PASSWORD. CI фронтенда дополнительно поднимает полный Docker-стек и прогоняет сценарий через nginx.

## 9. Частые ошибки
- `UnsupportedClassVersionError`: `java -version` должен показывать 21+, JAVA_HOME должен указывать на JDK.
- `Port 8080 already in use`: остановите прежнюю копию либо передайте `--server.port=8081` и измените Vite proxy.
- `Database may be already in use`: другой процесс использует тот же файл H2; остановите его, не удаляйте БД.
- `Connection refused` от PostgreSQL: проверьте `docker compose ps`, профиль postgres и имя хоста (`postgres` внутри Compose).
- `Cannot find symbol Interests`: используйте рабочую ветку целиком, а не отдельные скопированные файлы.
- Демо не видно: запуск должен содержать `--nexus.demo=true`, либо NEXUS_DEMO=true.
- `null value in column category of relation interests`: обновите рабочую ветку и пересоберите backend. Модель сохраняет обязательную категорию из старой базы, новые интересы получают «Общее». Существующие категории и данные сохраняются; удалять Docker volume не требуется.

Версия предназначена для локальной демонстрации: загрузка фото, ML, модерация и восстановление пароля ещё не реализованы.
