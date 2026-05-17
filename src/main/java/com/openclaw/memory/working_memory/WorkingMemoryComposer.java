package com.openclaw.memory.working_memory;

import com.openclaw.memory.blackboard.Artifact;
import com.openclaw.memory.retrieval.RetrievalResult;
import com.openclaw.memory.retrieval.Retriever;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Composes working memory context for a given query.
 *
 * Pipeline:
 *   retrieve → filter superseded → score salience → sort → conflict-resolve
 *   → build timeline → assemble prompt
 */
@Slf4j
public class WorkingMemoryComposer {

    private final Retriever retriever;
    private final ConflictResolver conflictResolver;
    private final TemporalResolver temporalResolver;
    private final int maxContextTokens;
    private final LocalDateTime currentTime;

    // ── Constructors ──────────────────────────────────────────────────────────

    public WorkingMemoryComposer(Retriever retriever,
                                 com.openclaw.memory.graph.TemporalGraphManager graphManager,
                                 com.openclaw.memory.agents.conflict.ConflictResolutionAgent conflictAgent) {
        this(retriever,
             (memories, context) -> memories,
             (artifact, atTime) -> artifact == null
                     || graphManager == null
                     || graphManager.isConsistent(artifact, atTime),
             4000);
    }

    public WorkingMemoryComposer(Retriever retriever,
                                 ConflictResolver conflictResolver,
                                 TemporalResolver temporalResolver,
                                 int maxContextTokens) {
        this.retriever        = retriever;
        this.conflictResolver = conflictResolver;
        this.temporalResolver = temporalResolver;
        this.maxContextTokens = maxContextTokens;
        this.currentTime      = LocalDateTime.now();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public WorkingMemoryContext compose(String query, CompositionOptions options) {
        long startTime = System.currentTimeMillis();
        log.info("Composing working memory for query: {}", query);

        Instant now = Instant.now();
        SalienceScorer scorer = new SalienceScorer();

        // 1. Retrieve candidates
        List<RetrievalResult> candidates =
                retriever.search(query, options.maxCandidates).join();

        // 2. Convert → SelectedMemory, applying temporal + supersession filters
        List<SelectedMemory> scored = new ArrayList<>();
        for (RetrievalResult result : candidates) {
            if (!temporalResolver.isValid(result.artifact(), currentTime)) {
                log.debug("Skipping {} — temporally invalid", result.memoryId());
                continue;
            }
            if (isSuperseded(result)) {
                log.debug("Skipping {} — superseded", result.memoryId());
                continue;
            }
            if (result.score() < options.confidenceThreshold) {
                log.debug("Skipping {} — below confidence threshold", result.memoryId());
                continue;
            }

            SelectedMemory m = new SelectedMemory(
                    result.artifact(), result.score(),
                    SelectionReason.RELEVANCE_MATCH, result.memoryId());
            m.retrievalExplanation = result;
            m.content  = result.content();
            m.salience = scorer.score(m, now);
            scored.add(m);
        }

        // 3. Sort by salience descending, then limit
        scored.sort(Comparator.comparingDouble((SelectedMemory m) -> m.salience).reversed());
        List<SelectedMemory> limited = scored.stream()
                .limit(options.maxMemoriesPerContext)
                .collect(Collectors.toList());

        // 4. Resolve conflicts
        int beforeResolution = limited.size();
        List<SelectedMemory> resolvedMemories = conflictResolver.resolve(limited, query);
        int conflictsResolved = beforeResolution - resolvedMemories.size();

        // 5. Build timeline (chronological order by validFrom)
        List<SelectedMemory> timeline = resolvedMemories.stream()
                .sorted(Comparator.comparing(
                        SalienceScorer::validFromOf,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        // 6. Causal chains + prompt
        Map<String, List<String>> causalChains = buildCausalChains(resolvedMemories);
        String composedContext = assemblePrompt(resolvedMemories, causalChains, options);

        long elapsed = System.currentTimeMillis() - startTime;

        return new WorkingMemoryContext(
                query, resolvedMemories, composedContext, causalChains,
                new CompositionMetadata(elapsed, options.maxMemoriesPerContext,
                        resolvedMemories.size(), conflictsResolved),
                timeline);
    }

    public WorkingMemoryContext composeContext(String query) {
        return compose(query, new CompositionOptions());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static boolean isSuperseded(RetrievalResult result) {
        if (result.artifact() != null && result.artifact().getMetadata() != null) {
            if (result.artifact().getMetadata().get("supersededBy") != null) return true;
        }
        if (result.metadata() != null && result.metadata().get("supersededBy") != null) {
            return true;
        }
        return false;
    }

    private Map<String, List<String>> buildCausalChains(List<SelectedMemory> memories) {
        Map<String, List<String>> chains = new HashMap<>();
        for (SelectedMemory memory : memories) {
            if (memory.artifact == null) continue;
            Artifact artifact = memory.artifact;
            List<String> sources = new ArrayList<>();
            if (artifact.getProvenance() != null
                    && artifact.getProvenance().getSourceEventIds() != null) {
                sources.addAll(artifact.getProvenance().getSourceEventIds());
            }
            chains.put(artifact.getArtifactId(), sources);
        }
        return chains;
    }

    private String assemblePrompt(List<SelectedMemory> memories,
                                   Map<String, List<String>> causalChains,
                                   CompositionOptions options) {
        StringBuilder sb = new StringBuilder();
        int tokenCount = 0;

        sb.append("# Working Memory Context\n\n");

        for (SelectedMemory memory : memories) {
            if (tokenCount >= maxContextTokens) {
                log.info("Token limit reached — stopping context assembly");
                break;
            }

            String id   = memory.artifact != null
                    ? memory.artifact.getArtifactId() : memory.artifactId;
            String type = memory.artifact != null
                    ? String.valueOf(memory.artifact.getType()) : "UNKNOWN";
            String memContent = memory.artifact != null
                    ? memory.artifact.getContent() : memory.content;

            sb.append("## Memory: ").append(id).append("\n");
            sb.append("- Type: ").append(type).append("\n");
            sb.append("- Relevance: ").append(String.format("%.2f", memory.relevanceScore)).append("\n");
            sb.append("- Salience: ").append(String.format("%.3f", memory.salience)).append("\n");
            sb.append("- Reason: ").append(memory.selectionReason).append("\n");

            if (memory.artifact != null && memory.artifact.getProvenance() != null) {
                sb.append("- Source: ")
                        .append(memory.artifact.getProvenance().getSourceAgent()).append("\n");
                sb.append("- Timestamp: ")
                        .append(memory.artifact.getProvenance().getTimestamp()).append("\n");
            }
            if (memContent != null) {
                sb.append("- Content: ").append(memContent).append("\n\n");
            }

            tokenCount += estimateTokens(memContent);
        }

        if (!causalChains.isEmpty() && options.includeExplanation) {
            sb.append("\n# Causal Dependencies\n\n");
            for (Map.Entry<String, List<String>> entry : causalChains.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    sb.append("- ").append(entry.getKey()).append(" depends on: ")
                            .append(String.join(", ", entry.getValue())).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private static int estimateTokens(String text) {
        return text != null ? text.length() / 4 : 0;
    }

    // ===== Data Models =======================================================

    @Data
    public static class WorkingMemoryContext {
        private final String originalQuery;
        private final List<SelectedMemory> selectedMemories;
        private final String composedPrompt;
        private final Map<String, List<String>> causalChains;
        private final CompositionMetadata metadata;
        /** Memories sorted by validFrom ascending — the event timeline. */
        private final List<SelectedMemory> timeline;

        public WorkingMemoryContext(String originalQuery,
                                    List<SelectedMemory> selectedMemories,
                                    String composedPrompt,
                                    Map<String, List<String>> causalChains,
                                    CompositionMetadata metadata,
                                    List<SelectedMemory> timeline) {
            this.originalQuery    = originalQuery;
            this.selectedMemories = selectedMemories;
            this.composedPrompt   = composedPrompt;
            this.causalChains     = causalChains;
            this.metadata         = metadata;
            this.timeline         = timeline;
        }
    }

    @Data
    public static class SelectedMemory {
        public Artifact artifact;
        public double relevanceScore;
        public SelectionReason selectionReason;
        public String artifactId;
        public String content;
        public RetrievalResult retrievalExplanation;
        /** Composite salience score computed by {@link SalienceScorer}. */
        public double salience;

        public SelectedMemory(Artifact artifact, double score, SelectionReason reason, String id) {
            this.artifact       = artifact;
            this.relevanceScore = score;
            this.selectionReason = reason;
            this.artifactId     = id;
        }
    }

    public enum SelectionReason {
        RELEVANCE_MATCH,
        CAUSAL_DEPENDENCY,
        TEMPORAL_PROXIMITY,
        CONFLICT_RESOLUTION
    }

    @Data
    public static class CompositionMetadata {
        public final long totalTimeMs;
        public final int  maxMemoriesRequested;
        public final int  memoriesSelected;
        /** Number of duplicate-subject candidates removed by the ConflictResolver. */
        public final int  conflictsResolved;

        public CompositionMetadata(long totalTimeMs, int maxMemoriesRequested,
                                   int memoriesSelected, int conflictsResolved) {
            this.totalTimeMs          = totalTimeMs;
            this.maxMemoriesRequested = maxMemoriesRequested;
            this.memoriesSelected     = memoriesSelected;
            this.conflictsResolved    = conflictsResolved;
        }
    }

    @Data
    public static class CompositionOptions {
        public int    maxMemoriesPerContext = 20;
        public int    maxCandidates        = 100;
        public double confidenceThreshold  = 0.5;
        public boolean includeExplanation  = true;
    }

    // ===== Interfaces =========================================================

    public interface ConflictResolver {
        List<SelectedMemory> resolve(List<SelectedMemory> memories, String context);
    }

    public interface TemporalResolver {
        boolean isValid(Artifact artifact, LocalDateTime atTime);
    }
}
