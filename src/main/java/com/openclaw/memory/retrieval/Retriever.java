package com.openclaw.memory.retrieval;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Контракт для любого retrieval-движка.
 * WorkingMemoryComposer, MCPMemoryTools и все остальные
 * потребители зависят ТОЛЬКО от этого интерфейса.
 */
public interface Retriever {

    /**
     * Поиск по запросу.
     *
     * @param query  поисковый запрос на естественном языке
     * @param topK   максимальное кол-во результатов
     * @return список результатов, отсортированных по убыванию score
     */
    CompletableFuture<List<RetrievalResult>> search(String query, int topK);

    /** Вариант с дефолтным topK = 10 */
    default CompletableFuture<List<RetrievalResult>> search(String query) {
        return search(query, 10);
    }
}