package com.openclaw.memory.retrieval;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Decorator that caches retrieval results using Guava's in-process cache.
 * Cache key = query + ":" + topK. TTL is configurable per instance.
 */
public class CachingRetriever implements Retriever {

    private static final Logger log = LoggerFactory.getLogger(CachingRetriever.class);

    private final Retriever delegate;
    private final Cache<String, List<RetrievalResult>> cache;

    public CachingRetriever(Retriever delegate, int ttlMinutes) {
        this.delegate = delegate;
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(1_000)
                .recordStats()
                .build();
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> search(String query, int topK) {
        String key = query + '' + topK;   // unit-separator avoids collisions
        List<RetrievalResult> cached = cache.getIfPresent(key);

        if (cached != null) {
            log.debug("Cache hit for query='{}' topK={}", query, topK);
            return CompletableFuture.completedFuture(Objects.requireNonNull(cached));
        }

        return delegate.search(query, topK).thenApply(results -> {
            cache.put(key, results);
            log.debug("Cache miss — stored {} results for query='{}'", results.size(), query);
            return results;
        });
    }

    public void invalidate(String query, int topK) {
        cache.invalidate(query + '' + topK);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    /** Exposes Guava stats for Prometheus or logging. */
    public com.google.common.cache.CacheStats stats() {
        return cache.stats();
    }
}
