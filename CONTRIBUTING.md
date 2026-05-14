# Руководство по разработке Memory Module

## 🎯 Миссия

Memory Module - это runtime система когнитивной памяти для автономных AI агентов. Мы строим **детерминированную, управляемую провенансом** систему, которая реконструирует время, причинность, состояние и эволюцию убеждений.

## 📋 Требования для разработчиков

### Знание архитектуры
- Обязательно прочитайте [ARCHITECTURE_RU.md](ARCHITECTURE_RU.md)
- Поймите роль каждого из 12 агентов
- Изучите Blackboard архитектуру

### Принципы разработки

#### 1. **Агентная архитектура** 🤖
```
ПРАВИЛО: Каждый компонент - это специализированный агент
- ❌ Не создавайте монолитные сервисы
- ✅ Разбейте логику на специализированных агентов
- ✅ Каждый агент имеет одну отвественность
```

#### 2. **Провенанс везде** 📝
```
ПРАВИЛО: Каждый артефакт ДОЛЖЕН иметь провенанс
- ❌ Не создавайте артефакты без source_event_ids
- ✅ Всегда указывайте source_event_ids
- ✅ Всегда указывайте confidence score (0-1)
- ✅ Всегда указывайте lineage
```

Пример:
```java
Provenance provenance = new Provenance.Builder()
    .addSourceEventId("EVT-001")
    .addSourceEventId("EVT-002")
    .confidence(0.95f)
    .addLineage("ORG_EVENT")
    .addLineage("TRANSFORMED")
    .putMetadata("reason", "hybrid_search_fusion")
    .build();

Artifact artifact = new Artifact.Builder()
    .artifactId("ART-" + UUID.randomUUID())
    .producedBy(agent.getName())
    .type(Artifact.ArtifactType.MEMORY)
    .provenance(provenance)  // ОБЯЗАТЕЛЬНО!
    .content(results)
    .build();

blackboard.publishArtifact(artifact);
```

#### 3. **Неизменяемость** 🔒
```
ПРАВИЛО: Все артефакты неизменяемы после публикации
- ❌ Не изменяйте опубликованные артефакты
- ✅ Создавайте новые версии с chain of supersession
- ✅ Event Store только append-only
- ✅ Никаких удалений из лога
```

#### 4. **Гибридный поиск** 🔍
```
ПРАВИЛО: Поиск ВСЕГДА гибридный
- ❌ Не используйте только vector search
- ❌ Не используйте только BM25
- ✅ Всегда комбинируйте BM25 + Vector + Rerank
- ✅ Используйте QMD оркестрацию
```

---

## 🛠️ Как начать разработку

### 1. Установка окружения

```bash
# Клонировать проект
git clone <repository>
cd memory-module

# Требования
# - Java 21+
# - Maven 3.8+

# Проверить версию Java
java -version

# Проверить Maven
mvn --version
```

### 2. Структура проекта

```
src/main/java/com/openclaw/memory/
├── agents/                    # Все 12 агентов
│   ├── orchestrator/         # Оркестратор (главный)
│   ├── event_store/          # Хранилище событий
│   ├── retrieval/            # Гибридный поиск
│   ├── graph/                # Граф причинности
│   ├── semantic/             # Семантическая память
│   ├── multimodal/           # Мультимодальность
│   ├── working_memory/       # Рабочая память
│   ├── conflict/             # Разрешение конфликтов
│   ├── indexing/             # Индексирование DAG
│   ├── architect/            # Архитектор
│   ├── observability/        # Мониторинг
│   └── qa/                   # QA/Тестирование
├── blackboard/               # Шина коммуникации
├── event_store/              # Интерфейсы Event Store
├── storage/                  # Слой хранилища
├── api/                      # REST Controllers
├── mcp/                      # Model Context Protocol
├── cli/                      # CLI инструменты
└── config/                   # Конфигурация
```

### 3. Создание нового агента

**Шаг 1**: Создать интерфейс в `src/main/java/com/openclaw/memory/agents/{domain}/`

```java
package com.openclaw.memory.agents.semantic;

import com.openclaw.memory.agents.BaseAgent;

public interface MyAgent extends BaseAgent {
    // Методы специфичные для этого агента
}
```

**Шаг 2**: Создать реализацию

```java
package com.openclaw.memory.agents.semantic;

public class MyAgentImpl implements MyAgent {
    private static final Logger logger = LoggerFactory.getLogger(MyAgentImpl.class);
    
    private final MemoryBlackboard blackboard;
    private final EventStore eventStore;
    private volatile AgentStatus status = AgentStatus.INITIALIZING;
    
    // Metrics
    private final AtomicLong tasksCompleted = new AtomicLong(0);
    private final AtomicLong tasksFailed = new AtomicLong(0);
    
    public MyAgentImpl(MemoryBlackboard blackboard, EventStore eventStore) {
        this.blackboard = blackboard;
        this.eventStore = eventStore;
    }
    
    @Override
    public String getName() {
        return "MY_AGENT";
    }
    
    @Override
    public void initialize() {
        status = AgentStatus.READY;
        logger.info("{} initialized", getName());
    }
    
    @Override
    public CompletableFuture<List<Artifact>> executeTask(Task task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                status = AgentStatus.BUSY;
                task.markInProgress();
                
                // Ваша логика здесь
                List<Artifact> results = new ArrayList<>();
                
                // ... реализация ...
                
                task.markComplete(results.stream()
                    .map(Artifact::getArtifactId)
                    .collect(Collectors.toList()));
                
                status = AgentStatus.READY;
                tasksCompleted.incrementAndGet();
                return results;
                
            } catch (Exception e) {
                task.markFailed(e.getMessage());
                status = AgentStatus.ERROR;
                tasksFailed.incrementAndGet();
                throw new RuntimeException(e);
            }
        });
    }
    
    // ... остальные методы интерфейса ...
}
```

**Шаг 3**: Зарегистрировать в Spring Configuration

```java
@Configuration
public class AgentConfiguration {
    
    @Bean
    public MyAgent myAgent(MemoryBlackboard blackboard, EventStore eventStore) {
        return new MyAgentImpl(blackboard, eventStore);
    }
}
```

---

## 📝 Код стиль

### Именование

**Переменные и методы**:
- `camelCase` для переменных и методов
- `UPPER_SNAKE_CASE` для констант

**Классы и интерфейсы**:
- `PascalCase` для названий
- Суффикс `Agent` для интерфейсов агентов
- Суффикс `Impl` или `Default` для реализаций

**Пакеты**:
```
com.openclaw.memory.{domain}.{subdomain}
Пример: com.openclaw.memory.agents.retrieval
```

### Структурирование класса

```java
public class MyAgent {
    
    // 1. Логирование
    private static final Logger logger = LoggerFactory.getLogger(MyAgent.class);
    
    // 2. Константы
    private static final String AGENT_NAME = "MY_AGENT";
    
    // 3. Зависимости
    private final MemoryBlackboard blackboard;
    private final EventStore eventStore;
    
    // 4. Состояние
    private volatile AgentStatus status;
    
    // 5. Метрики
    private final AtomicLong tasksCompleted = new AtomicLong(0);
    
    // 6. Конструктор
    public MyAgent(MemoryBlackboard blackboard, EventStore eventStore) {
        this.blackboard = blackboard;
        this.eventStore = eventStore;
    }
    
    // 7. Публичные методы (интерфейс)
    @Override
    public void initialize() { }
    
    // 8. Приватные помощники
    private void recordEvent(String type, Map<String, Object> data) { }
}
```

### JavaDoc

```java
/**
 * Запись события в Event Store
 * 
 * @param event событие для записи
 * @throws IllegalStateException если Event Store недоступен
 */
public void recordEvent(Event event) {
    // ...
}
```

---

## 🧪 Тестирование

### Unit тесты

```java
@SpringBootTest
public class MyAgentTest {
    
    @Mock
    private MemoryBlackboard blackboard;
    
    @Mock
    private EventStore eventStore;
    
    private MyAgent agent;
    
    @BeforeEach
    void setup() {
        agent = new MyAgentImpl(blackboard, eventStore);
    }
    
    @Test
    void testInitialization() {
        agent.initialize();
        assertEquals(BaseAgent.AgentStatus.READY, agent.getStatus());
    }
    
    @Test
    void testTaskExecution() {
        Task task = new Task.Builder()
            .id("TEST-001")
            .agent("MY_AGENT")
            .objective("test objective")
            .acceptanceCriteria(true, true, true, true)
            .build();
        
        CompletableFuture<List<Artifact>> result = agent.executeTask(task);
        
        // Assertions
        assertNotNull(result);
    }
}
```

### Интеграционные тесты

```java
@SpringBootTest
@IntegrationTest
public class AgentIntegrationTest {
    
    @Autowired
    private MemoryBlackboard blackboard;
    
    @Autowired
    private EventStore eventStore;
    
    @Autowired
    private OrchestratorAgent orchestrator;
    
    @Test
    void testFullOrchestration() {
        // Полный цикл оркестрации
        List<Task> decomposed = orchestrator
            .decomposeObjective("solve problem X", List.of());
        
        assertTrue(!decomposed.isEmpty());
    }
}
```

---

## 🔍 Отладка

### Включить DEBUG логирование

```properties
# application.yml
logging:
  level:
    com.openclaw.memory: DEBUG
    com.openclaw.memory.agents: TRACE
```

### Утилиты отладки

```java
// Получить текущее состояние blackboard
MemoryBlackboard.StateSnapshot snapshot = blackboard.createStateSnapshot();
System.out.println("Artifacts: " + snapshot.artifactCount);
System.out.println("Tasks: " + snapshot.taskCount);
System.out.println("Conflicts: " + snapshot.conflictCount);
```

---

## 📊 Производительность

### Профилирование

```bash
# С JFR (Java Flight Recorder)
mvn clean test -Dcom.sun.jdwp.options=transport=dt_socket,server=y,suspend=n,address=5005
```

### Основные метрики для отслеживания

- Task decomposition time
- Event recording latency
- Artifact publication latency
- Query execution time
- Cache hit rate

---

## 🔒 Требования безопасности

1. **Провенанс аудит**
   - Все события записываются в Event Store
   - Полная трассировка происхождения

2. **Целостность данных**
   - Append-only гарантирует неизменяемость
   - Conflict Resolution обеспечивает консистентность

3. **Авторизация**
   - Все запросы должны быть аутентифицированы
   - Контроль доступа на уровне артефактов

---

## 📮 Pull Request процесс

1. Fork репозитория
2. Создать feature branch: `git checkout -b feature/my-feature`
3. Commit изменений: `git commit -am 'Add new feature'`
4. Push в branch: `git push origin feature/my-feature`
5. Создать Pull Request
6. Дождаться review и CI/CD

### PR требования

- ✅ Все тесты должны проходить
- ✅ Coverage должен быть ≥ 80%
- ✅ Code review от минимум одного человека
- ✅ CI/CD pipeline success

---

## 🆘 Помощь и вопросы

- Прочитайте документацию в `docs/`
- Посмотрите существующие реализации агентов
- Создайте Issue для вопросов
- Начните Discussion для архитектурных вопросов

---

## 📜 Лицензия

By contributing to Memory Module, you agree that your contributions will be licensed under the MIT License.

---

**Спасибо за вклад в Memory Module! ❤️**
