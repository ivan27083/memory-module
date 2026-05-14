# Memory Module - Multi-Agent Cognitive Memory Runtime

![Version](https://img.shields.io/badge/version-0.1.0--SNAPSHOT-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![License](https://img.shields.io/badge/license-MIT-green)

## 🧠 О системе

**Memory Module** — это детерминированная, управляемая провенансом система памяти для автономных AI агентов (Multi-Agent Cognitive Memory Runtime - MACMR).

Это **не** библиотека для RAG, **не** обёртка над векторной БД, а полнофункциональная **runtime система** для когнитивной памяти, которая:

- 🔄 Реконструирует **время** (временные зависимости)
- 📊 Отслеживает **причинность** (причинно-следственные связи)
- 🎯 Управляет **состоянием** (статефулность)
- 💭 Сохраняет **намерение** (intent preservation)
- 📈 Отслеживает **эволюцию убеждений** (belief dynamics)

---

## 🏗️ Архитектура

### 12 Специализированных Агентов

```
┌─────────────────────────────────────────────────┐
│         Memory Blackboard (Central Bus)         │
├─────────────────────────────────────────────────┤
│  Task Queue | Artifacts | Conflicts | Traces    │
└────────┬────────────────────────────┬───────────┘
         │                            │
    ┌────┴─────────────────────────┬──┴────┐
    │                              │       │
[Orchestrator] [Event Store] [Retrieval] [Graph]
    │              │              │        │
[Semantic]  [Multimodal] [Working Memory] [Conflict]
    │              │              │        │
[Indexing]  [QA] [Architect]  [Observability]
```

### Ключевые компоненты:

1. **Orchestrator Agent** ⭐ - Декомпозирует задачи и управляет выполнением
2. **Event Store Agent** - Неизменяемый лог (DuckDB + Parquet)
3. **Retrieval Agent** - Гибридный поиск (BM25 + Vector + Rerank)
4. **Graph Agent** - Граф причинности (Neo4j)
5. **Semantic Memory Agent** - Дистиллированные факты
6. **Multimodal Agent** - Текст, код, изображения, логи
7. **Working Memory Agent** - Контекст и разрешение противоречий
8. **Conflict Resolution Agent** - Обнаружение конфликтов
9. **Indexing Agent** - DAG конвейеры
10. **Architect Agent** - Валидация инвариантов
11. **Observability Agent** - Метрики и мониторинг
12. **QA Agent** - Тестирование и гейты

---

## 🚀 Быстрый старт

### Требования
- Java 21+
- Maven 3.8+
- Docker (опционально)

### Запуск

```bash
# Собрать проект
mvn clean install

# Запустить приложение
mvn spring-boot:run

# API доступен на http://localhost:8080
```

### Docker

```bash
docker build -t memory-module:0.1.0 .
docker run -p 8080:8080 memory-module:0.1.0
```

---

## 📚 Документация

- [Архитектура](docs/ARCHITECTURE_RU.md) - Подробное описание системы
- [API документация](docs/api.md) - REST API endpoints
- [Примеры использования](docs/examples.md) - Примеры интеграции

---

## 🔧 Технологический стек

- **Java 21** - Язык программирования
- **Spring Boot 3.3.5** - Framework
- **DuckDB** - Event Store (append-only лог)
- **Neo4j** - Граф причинности
- **LanceDB** - Векторный поиск
- **Prometheus** - Метрики
- **Docker** - Контейнеризация

---

## ✨ Ключевые характеристики

### ✅ Провенанс везде
Каждый артефакт содержит: source_event_ids, timestamp, confidence score

### ✅ Неизменяемость
Event Store строго append-only. Никаких мутаций или удалений.

### ✅ Гибридный поиск
BM25 + Vector Search + Reranking + QMD Orchestration

### ✅ Временная корректность
Полная поддержка временных интервалов и причинности

### ✅ Видимость конфликтов
Конфликты — сущности первого класса с полной трассировкой

---

## 📊 Целевые показатели производительности

| Метрика | Цель |
|---------|------|
| Кешированный поиск | <100ms |
| Полный поиск | <300ms |
| Масштабируемость | Миллионы событий |

---

## 🧪 Тестирование

```bash
# Все тесты
mvn test

# С покрытием
mvn test jacoco:report

# Интеграционные тесты
mvn verify
```

---

## 🔍 Быстрые примеры

### Запись события

```java
Event event = new Event.Builder()
    .eventId("EVT-001")
    .sourceAgent("retrieval-agent")
    .eventType(Event.EventType.MEMORY_RETRIEVED)
    .addPayloadEntry("query", "what is AI?")
    .build();

eventStore.appendEvent(event);
```

### Публикация артефакта

```java
Provenance provenance = new Provenance.Builder()
    .addSourceEventId("EVT-001")
    .confidence(0.95f)
    .build();

Artifact artifact = new Artifact.Builder()
    .artifactId("ART-001")
    .producedBy("retrieval-agent")
    .type(Artifact.ArtifactType.MEMORY)
    .provenance(provenance)
    .build();

blackboard.publishArtifact(artifact);
```

---

## 📄 Лицензия

MIT License

---

## 📞 Контакты

- 🐛 Issues: https://github.com/openclaw/memory-module/issues
- 💬 Discussions: https://github.com/openclaw/memory-module/discussions

---

**Made with ❤️ for OpenClaw AI Agents**
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
