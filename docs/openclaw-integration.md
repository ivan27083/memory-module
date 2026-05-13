# Чеклист интеграции OpenClaw

Markdown-промпт требует уточнений перед прямой интеграцией с OpenClaw runtime. Перед добавлением OpenClaw-specific адаптера предоставьте эти файлы или контракты:

- структура репозитория,
- provider-интерфейсы,
- orchestration pipeline,
- логика сборки контекста,
- tool system,
- конфигурация dependency injection,
- runtime lifecycle entry points.

## Предлагаемая форма адаптера

Создайте отдельный inbound-адаптер, который переводит события OpenClaw в вызовы `MemoryFacade`:

- conversation turn started -> retrieve context,
- user or tool event finalized -> write episodic memory,
- short-lived turn state changed -> write working memory,
- consolidation signal -> write semantic wiki memory,
- external document added -> вызвать `RagIngestionService`.

Адаптер не должен изменять внутренности providers или заменять существующую orchestration. Он должен обогащать контекст через явный retrieval step и сохранять rollback простым: достаточно отключить adapter bean.
