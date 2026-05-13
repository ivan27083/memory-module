# memory-module-http OpenClaw Plugin

Локальный OpenClaw plugin для подключения внешнего Spring Boot `memory-module`.

## Tools

- `memory_module_retrieve`
- `memory_module_write`
- `memory_module_rag_ingest`

## Hooks

- `before_prompt_build`: получает релевантную память из `memory-module` и добавляет ее в контекст.
- `agent_end`: сохраняет завершенный turn как `EPISODIC` memory.

## Config

```json
{
  "plugins": {
    "entries": {
      "memory-module-http": {
        "enabled": true,
        "config": {
          "enabled": true,
          "baseUrl": "http://localhost:8088",
          "agentId": "openclaw-main",
          "retrieveLimit": 5,
          "autoInject": true,
          "autoCapture": true,
          "captureMaxChars": 4000,
          "timeoutMs": 5000
        }
      }
    }
  }
}
```
