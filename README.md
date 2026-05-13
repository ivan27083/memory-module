# OpenClaw Memory Module

Каркас внешней многоуровневой подсистемы памяти, созданный на основе `memory_system_prompt.md`.

## Подтвержденные вводные

- Обязательный стек: Java, Spring Boot, PostgreSQL, Redis, Qdrant, Docker.
- Обязательная архитектура: рабочая память, эпизодическая память, семантическая wiki-память, векторный retrieval, внешний RAG, оркестрация retrieval, асинхронное управление жизненным циклом.
- Политика интеграции: не изменять ядро OpenClaw; интегрироваться через адаптеры и сервисные интерфейсы.

## Неизвестные вводные

Исходный markdown явно запрещает выдумывать внутренние детали OpenClaw. Поэтому проект предоставляет стабильные порты и HTTP API, но не предполагает существование конкретных provider-интерфейсов OpenClaw, lifecycle hooks runtime, orchestration flow, tool contracts или API сборки контекста.

## Структура директорий

```text
src/main/java/com/openclaw/memory
  application/        Use case-сервисы и сервисы оркестрации
  config/             Конфигурация Spring и свойства модуля
  domain/model/       Модель данных памяти и retrieval
  domain/port/        Порты clean architecture
  adapter/in/web/     HTTP-адаптер для внешней интеграции
  adapter/out/*       Адаптеры Redis, PostgreSQL, Qdrant, embeddings, RAG
src/main/resources
  db/migration/       Миграции схемы PostgreSQL
docs/                 Заметки по архитектуре и интеграции
```

## Локальный запуск

```bash
docker compose up -d
mvn spring-boot:run
```

Если Maven не установлен локально, соберите проект через Docker:

```bash
docker build -t openclaw-memory-module .
docker run --rm -p 8088:8088 --network host openclaw-memory-module
```

## Настройка подключения

По умолчанию приложение подключается к PostgreSQL так:

```text
jdbc:postgresql://localhost:55432/memory_module
username: memory
password: memory
```

Эти значения можно переопределить через переменные окружения:

```bash
MEMORY_DB_URL=jdbc:postgresql://localhost:55432/memory_module
MEMORY_DB_USERNAME=memory
MEMORY_DB_PASSWORD=memory
```

Если при запуске появляется ошибка `SQL State : 28P01` или сообщение `пользователь "memory" не прошёл проверку подлинности`, приложение дошло до PostgreSQL, но пароль не совпал. Обычно это значит, что приложение подключилось не к проектному контейнеру или Docker volume был создан раньше с другим паролем.

Проектный PostgreSQL публикуется на порту `55432`, чтобы не конфликтовать с локальным PostgreSQL на `5432`. Проверьте, что в IDE не переопределена переменная `MEMORY_DB_URL` на старый порт, либо задайте корректные `MEMORY_DB_USERNAME` и `MEMORY_DB_PASSWORD`. Для полностью чистого локального окружения можно пересоздать Docker volume, но это удалит данные PostgreSQL контейнера.

Qdrant использует коллекцию `agent_memory`. При повторном запуске существующая коллекция считается нормальным состоянием и переиспользуется.

Если `/api/memory/retrieve` возвращает пустой массив, но приложение не падает, проверьте LM Studio embedding endpoint:

```powershell
Invoke-RestMethod http://localhost:1234/v1/models
```

Имя модели в `MEMORY_EMBEDDING_MODEL` должно совпадать с `id` загруженной embedding-модели. Размерность модели должна совпадать с `MEMORY_VECTOR_DIMENSIONS`; для уже созданной Qdrant-коллекции размерность изменить нельзя без пересоздания коллекции.

`/api/rag/ingest` синхронно вызывает LM Studio embeddings и Qdrant. Если команда долго не отвечает, сначала проверьте LM Studio напрямую:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:1234/v1/embeddings" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{
    "model": "ваш-id-embedding-модели",
    "input": "Embedding smoke test"
  }'
```

Внешние HTTP-вызовы ограничены таймаутами `MEMORY_HTTP_CONNECT_TIMEOUT` и `MEMORY_HTTP_READ_TIMEOUT`.

В PowerShell 5 кириллица в JSON body иногда уходит не как UTF-8. Если в ответах видны `????`, отправляйте тело как UTF-8 bytes:

```powershell
$json = '{
  "agentId": "agent-dev",
  "sessionId": "session-1",
  "type": "WORKING",
  "content": "Пользователь тестирует memory-module.",
  "metadata": {
    "source": "smoke-test"
  }
}'

Invoke-RestMethod `
  -Uri "http://localhost:8088/api/memory/write" `
  -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
```

## Стратегия интеграции

OpenClaw должен вызывать этот модуль через тонкий адаптер, который отображает существующие события жизненного цикла агента на:

- `POST /api/memory/write`
- `POST /api/memory/retrieve`
- `POST /api/rag/ingest`

Когда реальные интерфейсы OpenClaw будут доступны, добавьте отдельный inbound-адаптер рядом с `adapter/in/web`, не связывая логику памяти напрямую с ядром runtime.

## Фазы реализации

1. Проверить структуру проекта OpenClaw, границы DI, lifecycle hooks, provider-интерфейсы и flow сборки контекста.
2. Добавить модуль OpenClaw-адаптера, содержащий только логику маппинга.
3. Включить реальные embeddings и индексацию Qdrant в staging-окружении.
4. Добавить тесты релевантности retrieval и сценарии консистентности длинного контекста.
5. Добавить async workers для консолидации и benchmark-и поведения batching.
