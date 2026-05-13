# Архитектура

## Граница модуля

Этот репозиторий является внешней подсистемой. Он отвечает за персистентность памяти, оркестрацию retrieval, ingestion для RAG и обработку жизненного цикла памяти. Интеграция с OpenClaw должна быть реализована как адаптер, который зависит от application-портов этого модуля, а не как изменения внутри core orchestration OpenClaw.

## Слои

- Domain: записи памяти, retrieval-запросы, векторные документы и metadata chunk-ов.
- Ports: контракты хранилищ, векторного индекса, embeddings, reranking и внешнего RAG.
- Application: write pipeline, retrieval orchestration, consolidation и ingestion use case-ы.
- Adapters: working memory в Redis, episodic/wiki memory в PostgreSQL, Qdrant vector index, OpenAI-compatible embeddings, входящий HTTP API.

## Поток данных

1. Agent runtime отправляет события памяти во входящий адаптер.
2. Working memory сохраняется в Redis с TTL.
3. Episodic memory сохраняется в PostgreSQL.
4. Semantic wiki memory сохраняется в PostgreSQL и в будущем может зеркалироваться в markdown через filesystem-адаптер.
5. Embeddings генерируются через OpenAI-compatible endpoint.
6. Векторы индексируются в Qdrant.
7. Retrieval orchestration отдельно опрашивает working, episodic, semantic, vector и external RAG источники.
8. Reranking возвращает вызывающей стороне ограниченный набор контекста.

## Архитектурные решения

### Memory и RAG разделены

Внешние документы извлекаются через `ExternalKnowledgeRetriever`, а память агента использует отдельные memory-порты. Это не дает корпусам документов смешиваться с персональной или сессионной памятью.

### Qdrant является retrieval-индексом, а не основным хранилищем

PostgreSQL остается источником истины для episodic и semantic memory. Qdrant хранит vector payloads и ссылки на первичные записи.

### Интеграция OpenClaw только через адаптеры

Внутренние детали OpenClaw не предполагаются. Необходимый недостающий контекст:

- структура репозитория,
- provider-интерфейсы,
- orchestration pipeline,
- логика сборки контекста,
- tool system,
- конфигурация dependency injection,
- runtime lifecycle entry points.
