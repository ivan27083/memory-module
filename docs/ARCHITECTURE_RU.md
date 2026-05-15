# Multi-Agent Cognitive Memory Runtime (MACMR) — Полная архитектура

## Обзор системы

Это **не библиотека**, **не RAG обёртка** и **не инструмент для работы с векторными БД**.

Это **Multi-Agent Cognitive Memory Runtime (MACMR)** — детерминированная, управляемая провенансом система памяти для автономных AI агентов, которая реконструирует:

- **время** (temporal integrity с `valid_from`/`valid_to`)
- **причинность** (causal graph reasoning)
- **состояние** (state reconstruction)
- **намерение** (intent preservation)
- **эволюцию убеждений** (belief revision + supersession chains)

---

## CORE: Гибридный конвейер извлечения (QMD Engine)

### QMD Retrieval Engine — Query Morphology Decomposition

**Файл:** [`QMDRetrievalEngine.java`](../src/main/java/com/openclaw/memory/retrieval/QMDRetrievalEngine.java)

Реализует **5-этапный конвейер** гибридного поиска:

```
1. Query Decomposition
   ├─ Лексическая (BM25)
   ├─ Векторная (семантика)
   ├─ HyDE (гипотезы)
   └─ Граф (расширение)

2. Candidate Retrieval (параллельно)
   ├─ BM25Retriever
   ├─ VectorRetriever
   └─ GraphRetriever

3. Fusion (RRF - Reciprocal Rank Fusion)
   → Объединение результатов от всех методов

4. Reranking (Cross-Encoder)
   → Финальная переоценка

5. Explanation Output
   → Провенанс + разбор оценок
```

**Ключевые возможности:**
- 🔄 **RRF Fusion**: `score = 1/(k + rank)` для каждого метода
- 📊 **Hybrid Scores**: bm25_score + vector_score + graph_score
- 🎯 **Query Expansion**: Автоматическое расширение запроса
- 📈 **Reranking**: Cross-encoder для финальной оптимизации

**Целевые метрики:**
- <100ms cached retrieval
- <300ms full retrieval
- Top-10 accuracy >85%

---

## Архитектурные принципы

### 1. Многоагентная оркестрация (MANDATORY)

Все системы ДОЛЖНЫ следовать модели многоагентной оркестрации со строгим разделением ответственности.

### 2. Управление провенансом (Provenance-First)

Каждый артефакт ДОЛЖЕН включать:
- `source_event_ids` — какие события его создали
- `timestamp` — когда он был создан
- `confidence` — оценка уверенности [0, 1]
- `lineage` — цепь происхождения + authors

### 3. Неизменяемость (Immutability)

- Event Store строго append-only
- Ни один артефакт не может быть изменён после публикации
- Все обновления создают цепи замещения (supersession chains)

### 4. Управление временем (Temporal Consistency)

Все рёбра графа включают:
- `valid_from: LocalDateTime` — начало валидности
- `valid_to: LocalDateTime` — конец валидности
- `confidence: double` — уверенность в связи

Это позволяет **временную разметку** памяти:
```java
// Пример: знание устаревает
edge = addEdge(fact1, fact2, SUPERSEDES,
    validFrom: 2024-01-01,
    validTo:   2024-06-01,
    confidence: 0.95
);

// Затем добавляем новое знание
edge2 = addEdge(fact1, fact3, SUPERSEDES,
    validFrom: 2024-06-01,
    validTo:   LocalDateTime.MAX,
    confidence: 0.98
);

// Запрос памяти в момент времени
facts = graph.traverse(startNode, atTime=2024-03-15);
// вернёт только валидные факты для этого момента
```

---

## 8 Основных компонентов (Production-Grade)

### 1. Working Memory Composer

**Файл:** [`WorkingMemoryComposer.java`](../src/main/java/com/openclaw/memory/working_memory/WorkingMemoryComposer.java)

Реконструирует контекст выполнения для агента:

```
Входящий запрос
    ↓
[1] QMD Retrieval (гибридный поиск)
    ↓
[2] Фильтрация (временная валидность + confidence)
    ↓
[3] Разрешение конфликтов
    ↓
[4] Построение причинных цепей
    ↓
[5] Сборка финального приглашения
    ↓
WorkingMemoryContext (выход)
```

**Выходной формат:**
```java
WorkingMemoryContext {
    selectedMemories:     List<Artifact>
    composedPrompt:       String
    causalChains:         Map<String, List<String>>
    metadata:             CompositionMetadata
}
```

---

### 2. Temporal Graph Manager

**Файл:** [`TemporalGraphManager.java`](../src/main/java/com/openclaw/memory/graph/TemporalGraphManager.java)

Управляет причинной логикой + временными интервалами:

```
TemporalNode (Entity, Memory, Event, Project, Fact)
    ↕
TemporalEdge
    ├─ type: CAUSES | UPDATES | SUPERSEDES | RELATES_TO
    ├─ validFrom: LocalDateTime
    ├─ validTo: LocalDateTime
    └─ confidence: [0, 1]
```

**API:**
```java
// Добавить узел
graph.addNode("entity_1", NodeType.ENTITY, data);

// Добавить связь с временным окном
graph.addEdge("from", "to", EdgeType.CAUSES,
    validFrom: LocalDateTime.now(),
    validTo: LocalDateTime.now().plus(1, ChronoUnit.DAYS),
    confidence: 0.95
);

// Запрос в момент времени
List<String> deps = graph.traverse("node", atTime, 
    TraversalType.BACKWARD, maxDepth=5);

// Цепь причин
CausalChain chain = graph.getCausalChain("node", atTime);

// Проверка согласованности
GraphConsistencyReport report = graph.validateConsistency(atTime);
```

---

### 3. Forgetting System (3-Tier)

**Файл:** [`ForgetSystem.java`](../src/main/java/com/openclaw/memory/storage/ForgetSystem.java)

Управляет распадом памяти + архивацией:

```
┌──────────────────────────────────────┐
│ Tier 1: Working Memory (LRU + salience)
│ - Активная 100 записей
│ - Быстрый доступ (< 10ms)
│ - Вытеснение по салиентности
└─────────────────┬────────────────────┘
                  ↓
         [Decay + Compression]
                  ↓
┌──────────────────────────────────────┐
│ Tier 2: Semantic Compression
│ - Сжатые ~1000 записей
│ - < 100ms доступ
│ - Форсированное архивирование
└─────────────────┬────────────────────┘
                  ↓
         [Cold Archive]
                  ↓
┌──────────────────────────────────────┐
│ Tier 3: Parquet Archive
│ - Все исторические записи
│ - Вторичное хранилище
│ - Теплое чтение <1s
└──────────────────────────────────────┘
```

**Салиентность:**
- Начальная: 1.0
-增 доступ: +0.1 за обращение (Tier1), +0.05 (Tier2)
- Decay: `score(t) = score(0) × exp(-0.001 × t_seconds)`

**API:**
```java
// Добавить
forget.remember(artifact);

// Получить (с автоматическим повышением)
Optional<Artifact> mem = forget.access("memId");

// Цикл забывания
ForgetCycleResult result = forget.runForgetCycle(percentileThreshold=25);
// Вытеснит 25% наименее салиентных из Tier1 в Tier2
```

---

### 4. Incremental Indexing Engine (CocoIndex-style DAG)

**Файл:** [`IncrementalIndexingEngine.java`](../src/main/java/com/openclaw/memory/indexing/IncrementalIndexingEngine.java)

DAG-конвейер с кешированием:

```
Raw Artifact
    ↓ [normalize]
Normalized
    ↓ [chunk]
Chunks
    ↓ [embed]
Embeddings
    ↓ [index]
Indexed
    ↓ [graph_update]
✓ Indexed
```

Каждый этап:
- ✅ Кешируется на основе входного хеша SHA-256
- ✅ Частично пересчитывается если входные данные изменились
- ✅ Может быть скопирован из кеша параллельно

**Производительность:**
- Primeira полная индексация: 50-200ms (зависит от размера)
- Cache hit: <2ms
- Batch parallelism: N threads

---

### 5. Multimodal Processor

**Файл:** [`MultimodalProcessor.java`](../src/main/java/com/openclaw/memory/multimodal/MultimodalProcessor.java)

Обрабатывает:
- **Text**: NLP токенизация + семантика
- **Code**: Tree-sitter AST + граф символов
- **Images**: OCR + CLIP эмбеддинги
- **Logs**: Парсинг + деяние

**Параллельная обработка:**
```java
MultimodalAnalysis analysis = processor.analyzeMultimodal(
    new MultimodalInput(text, code, images, logs)
);

// Выход: Fused embeddings + cross-modal relations
double[] fusedEmbedding = analysis.fusedEmbedding; // 768-dim
```

---

### 6. Conflict Resolution System

**Файл:** [`ConflictResolutionSystem.java`](../src/main/java/com/openclaw/memory/agents/conflict/ConflictResolutionSystem.java)

Обнаруживает + разрешает противоречия:

```
Обнаруженное противоречие
    ↓
Оценка тяжести
    (confidence × temporal_proximity)
    ↓
Выбор стратегии
    ├─ Bollean: HigherConfidenceStrategy
    ├─ Version: LatestVersionStrategy
    └─ Factual: ConsensusStrategy
    ↓
Belief Revision (создание supersession chain)
    ↓
✓ Разрешено
```

**Цепь верований:**
```java
revisionHistory = system.getRevisionHistory("memId");
// [Old Belief] → [New Belief #1] → [New Belief #2]
// С причинами + временными метками
```

---

### 7. Observability System (Prometheus)

**Файл:** [`ObservabilitySystem.java`](../src/main/java/com/openclaw/memory/agents/observability/ObservabilitySystem.java)

Метрики (Micrometer):

```
Retrieval Latency (ms)
  ├─ p50: 45ms
  ├─ p95: 150ms
  └─ p99: 280ms

Cache Metrics
  ├─ Hit Rate: 68%
  ├─ Tier1 Size: 98 items
  ├─ Tier2 Size: 342 items
  └─ Tier3 Size: 8921 items

Indexing
  ├─ Items/sec: 1250
  ├─ Avg latency: 0.8ms/item
  └─ Cache hit ratio: 72%

Agent Performance
  ├─ orchestrator: 98% success
  ├─ retrieval: 99% success
  └─ conflict_resolution: 95% success
```

---

### 8. MCP Memory Tools

**Файл:** [`MCPMemoryTools.java`](../src/main/java/com/openclaw/memory/mcp/MCPMemoryTools.java)

9 инструментов для агентов:

```
memory.search(query) → RetrievalResult[]
memory.store(content, type) → {memoryId, created_at}
memory.update(memoryId, content, reason) → {success}
memory.delete(memoryId, reason) → {archived}
memory.timeline(query, from, to) → Artifact[]
memory.conflicts() → {activeConflicts, details}
memory.explain(memoryId) → {explanation}
memory.forget(percentile) → {processed_count}
memory.pin(memoryId) → {pinned}
```

---

## Производительность (Целевые показатели)

---

## 12 Системных Агентов

### 1. **Orchestrator Agent** ⭐ (НОВЫЙ - КРИТИЧНЫЙ)
- Декомпозирует все задачи
- Назначает задачи специализированным агентам
- Контролирует порядок выполнения
- Валидирует гейты завершения

### 2. **Architect Agent**
- Определяет системные инварианты
- Контролирует архитектурные границы
- Валидирует правильность разложения

### 3. **Event Store Agent**
- Реализует неизменяемый лог событий (append-only)
- Гарантирует целостность провенанса
- Управляет DuckDB + Parquet хранилищем

### 4. **Retrieval Agent (QMD Integrator)**
- Реализует гибридный конвейер поиска
- Управляет BM25 + vector + rerank fusion
- Владеет декомпозицией запроса (lex/vec/hyde/expand)

### 5. **Graph Agent (Temporal Reasoning)**
- Реализует граф на основе Kuzu
- Управляет причинно-следственными связями
- Контролирует валидность временных интервалов

### 6. **Semantic Memory Agent**
- Поддерживает дистиллированные факты
- Управляет обновлениями убеждений
- Обрабатывает оценку уверенности + граф замещения

### 7. **Multimodal Agent**
- Обрабатывает изображения, логи, код, документы
- Интегрирует CLIP, OCR, tree-sitter
- Создает унифицированное пространство эмбеддингов

### 8. **Indexing Agent (CocoIndex-style DAG)**
- Строит инкрементальные конвейеры
- Гарантирует частичное пересчитывание
- Поддерживает граф инвалидации кеша

### 9. **Working Memory Agent**
- Реконструирует контекст выполнения
- Формирует финальное приглашение для агента
- Разрешает противоречия во время вывода

### 10. **Conflict Resolution Agent**
- Обнаруживает противоречия
- Поддерживает граф убеждений
- Управляет арбитражем уверенности

### 11. **Observability Agent**
- Собирает метрики и трассы
- Логирует решения о поиске
- Отслеживает задержки и производительность

### 12. **QA / Evaluation Agent**
- Валидирует инварианты корректности
- Запускает регрессионные тесты + бенчмарки
- Контролирует гейты приемки

---

## Архитектура коммуникации (Blackboard)

Все агенты коммуницируют через **Memory Blackboard** - единую шину обмена данными.

```
┌────────────────────────────────────────────────────────┐
│          Memory Blackboard (Central Bus)               │
├────────────────────────────────────────────────────────┤
│ • Task Queue                                           │
│ • Artifact Repository (append-only)                    │
│ • Conflict Reports                                     │
│ • State Snapshots                                      │
│ • Retrieval Traces                                     │
└────────────────────────────────────────────────────────┘
         ↓        ↓        ↓        ↓        ↓
    [Agent]  [Agent]  [Agent]  [Agent]  [Agent]
```

### Контракт артефакта

```yaml
artifact_id: string                  # Уникальный ID
produced_by: string                  # Какой агент создал
depends_on: [artifact_id]            # Зависимости
type: event | memory | graph | ...   # Тип артефакта
timestamp: datetime                  # Когда создан
provenance:
  source_event_ids: []               # Исходные события
  confidence: float (0-1)            # Уверенность
  lineage: []                        # Цепь происхождения
content: object                      # Полезная нагрузка
```

### Контракт задачи

```yaml
id: TASK-XXX
agent: string                        # Какому агенту назначена
objective: string                    # Цель
inputs: []                          # Входные артефакты
outputs: []                         # Ожидаемые выходы
acceptance_criteria:
  - deterministic: bool             # Должна ли быть детерминирована
  - reproducible: bool              # Воспроизводимость
  - provenance_valid: bool          # Валидность провенанса
  - test_required: bool             # Нужны ли тесты
status: pending | in_progress | done
```

---

## Жизненный цикл выполнения (STRICT)

### Шаг 1: Декомпозиция (Orchestrator Agent)
Разбить работу на атомарные задачи

### Шаг 2: Назначение
Распределить задачи между специализированными агентами

### Шаг 3: Параллельное выполнение
Агенты работают независимо

### Шаг 4: Публикация артефактов
Все выходы сохраняются в Blackboard

### Шаг 5: Интеграция (Working Memory Agent)
Слить выходы в согласованное состояние системы

### Шаг 6: Валидация конфликтов (Conflict Agent)
Разрешить противоречия

### Шаг 7: QA Гейт (QA Agent)
Валидировать инварианты

### Шаг 8: Слияние
Только одобренное QA состояние принимается

---

## Слои памяти

### 1. Event Store (Слой событий)
Неизменяемый append-only лог всех взаимодействий системы

**Технология**: DuckDB + Parquet

### 2. Semantic Memory (Слой семантики)
Дистиллированные факты с:
- Оценкой уверенности
- Окном валидности
- Графом замещения

### 3. Temporal Graph (Слой графа)
Модель причинно-следственных и временных связей

**Технология**: Neo4j / Kuzu

### 4. Multimodal Memory (Мультимодальный слой)
Унифицированное пространство эмбеддингов:
- текст
- код
- изображения
- логи

**Технология**: LanceDB + OpenAI CLIP

### 5. Retrieval Engine (Слой поиска)
Гибридная система поиска (QMD)

**Компоненты**:
- BM25 (лексический)
- Vector Search (семантический)
- Reranker (переранжирование)

### 6. Working Memory (Контекстный слой)
Реконструкция контекста выполнения во время вывода

---

## Глобальные инварианты (НЕ ПОДЛЕЖАТ ОБСУЖДЕНИЮ)

### 5.1 Провенанс везде
Каждая память ДОЛЖНА содержать:
- `source_event_ids`
- `timestamps`
- `confidence score`

### 5.2 Строгая неизменяемость
Event Store is strictly append-only.
Никаких мутаций или удалений.

### 5.3 Временная корректность
Никогда не удалять историческую истину.
Все обновления создают цепи замещения.

### 5.4 Видимость конфликтов
Конфликты — это сущности первого класса.

### 5.5 Правило гибридного поиска
Поиск ВСЕГДА содержит:
- BM25
- vector search
- reranking
- QMD оркестрация

---

## Структура проекта

```
memory-module/
├── src/main/java/com/openclaw/memory/
│   ├── agents/
│   │   ├── orchestrator/         # Оркестратор
│   │   ├── architect/            # Архитектор
│   │   ├── retrieval/            # Гибридный поиск
│   │   ├── graph/                # Граф причинности
│   │   ├── semantic/             # Семантическая память
│   │   ├── multimodal/           # Мультимодальность
│   │   ├── indexing/             # Индексирование DAG
│   │   ├── working_memory/       # Рабочая память
│   │   ├── conflict/             # Разрешение конфликтов
│   │   ├── observability/        # Наблюдаемость
│   │   └── qa/                   # QA/тестирование
│   ├── blackboard/               # Шина коммуникации
│   ├── event_store/              # Append-only лог событий
│   ├── storage/                  # Слой хранилища
│   ├── api/                      # REST API
│   ├── mcp/                      # Model Context Protocol
│   ├── cli/                      # CLI инструменты
│   └── config/                   # Конфигурация
├── benchmarks/                   # Бенчмарки производительности
└── tests/                        # Интеграционные тесты
```

---

## Целевые показатели производительности

- **Кешированный поиск**: <100ms
- **Полный поиск**: <300ms
- **Масштабируемость**: миллионы событий

---

## Правила разработки

### DO ✅
- Разделить на специализированных агентов
- Следовать контрактам (Task, Artifact, Provenance)
- Тестировать инварианты
- Документировать провенанс

### DON'T ❌
- Реализовать монолитные сервисы
- Обойти агентские границы
- Игнорировать требования провенанса
- Использовать только vector-поиск
- Пропустить временное моделирование

---

## Следующие шаги

1. ✅ Создать структуру проекта
2. ✅ Реализовать Blackboard архитектуру
3. ✅ Создать интерфейсы 12 агентов
4. 🔄 Реализовать Orchestrator Agent
5. 🔄 Реализовать Event Store Agent
6. 🔄 Реализовать Retrieval Agent (QMD)
7. ⏳ Создать API слой
8. ⏳ Реализовать интеграционные тесты
9. ⏳ Развернуть на Azure с K8s
