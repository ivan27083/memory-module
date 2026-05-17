package com.openclaw.memory.agents.conflict;

import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.working_memory.WorkingMemoryComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implements WorkingMemoryComposer.ConflictResolver.
 *
 * For each (agentId, subject) group in the candidate set, keeps only the
 * SelectedMemory with the highest relevanceScore (proxy for confidence at
 * read time). Memories without a subject are passed through unchanged.
 *
 * This is the read-time complement to write-time supersession (PR-5):
 * supersession handles the DB, this handles the retrieved candidate list
 * (which may include results from QMD or external sources that bypass the DB).
 */
@Component
public class SubjectConflictResolver implements WorkingMemoryComposer.ConflictResolver {

    private static final Logger log = LoggerFactory.getLogger(SubjectConflictResolver.class);

    @Override
    public List<WorkingMemoryComposer.SelectedMemory> resolve(
            List<WorkingMemoryComposer.SelectedMemory> memories, String context) {

        Map<String, WorkingMemoryComposer.SelectedMemory> bestBySubject = new LinkedHashMap<>();
        List<WorkingMemoryComposer.SelectedMemory> noSubject = new ArrayList<>();

        for (WorkingMemoryComposer.SelectedMemory m : memories) {
            String subject = extractSubject(m);

            if (subject == null) {
                noSubject.add(m);
                continue;
            }

            String groupKey = extractAgentId(m) + "" + subject;

            bestBySubject.merge(groupKey, m, (existing, candidate) -> {
                if (candidate.relevanceScore > existing.relevanceScore) {
                    log.debug("Conflict resolved: keeping {} (score={:.2f}) over {} (score={:.2f}) for subject='{}'",
                            candidate.artifactId, candidate.relevanceScore,
                            existing.artifactId,  existing.relevanceScore,
                            subject);
                    return candidate;
                }
                return existing;
            });
        }

        List<WorkingMemoryComposer.SelectedMemory> result = new ArrayList<>(bestBySubject.values());
        result.addAll(noSubject);

        int filtered = memories.size() - result.size();
        if (filtered > 0) {
            log.info("ConflictResolver removed {} conflicting candidates from context", filtered);
        }

        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String extractSubject(WorkingMemoryComposer.SelectedMemory m) {
        // Try Artifact metadata first (Blackboard path)
        if (m.artifact != null && m.artifact.getMetadata() != null) {
            Object s = m.artifact.getMetadata().get("subject");
            if (s != null) return s.toString();
        }
        // Fall back to RetrievalResult metadata (QMD / vector path)
        if (m.retrievalExplanation != null && m.retrievalExplanation.metadata() != null) {
            Object s = m.retrievalExplanation.metadata().get("subject");
            if (s != null) return s.toString();
        }
        return null;
    }

    private static String extractAgentId(WorkingMemoryComposer.SelectedMemory m) {
        if (m.artifact != null && m.artifact.getMetadata() != null) {
            Object a = m.artifact.getMetadata().get("agentId");
            if (a != null) return a.toString();
        }
        if (m.retrievalExplanation != null && m.retrievalExplanation.metadata() != null) {
            Object a = m.retrievalExplanation.metadata().get("agentId");
            if (a != null) return a.toString();
        }
        return "unknown";
    }

    /**
     * Converts a MemoryRecord to a minimal SelectedMemory for use in
     * test scenarios where full Artifact objects are not available.
     */
    public static WorkingMemoryComposer.SelectedMemory fromRecord(MemoryRecord record, double score) {
        WorkingMemoryComposer.SelectedMemory m = new WorkingMemoryComposer.SelectedMemory(
                null, score,
                WorkingMemoryComposer.SelectionReason.RELEVANCE_MATCH,
                record.id().toString()
        );
        m.content = record.content();
        return m;
    }

    /**
     * Utility: build a synthetic MemoryRecord for conflict detection from a
     * SelectedMemory (used in DefaultMemoryFacade pre-save detection).
     */
    static MemoryRecord toRecord(WorkingMemoryComposer.SelectedMemory m, String agentId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (m.artifact != null && m.artifact.getMetadata() != null) {
            meta.putAll(m.artifact.getMetadata());
        }
        if (m.retrievalExplanation != null && m.retrievalExplanation.metadata() != null) {
            meta.putAll(m.retrievalExplanation.metadata());
        }
        String content = m.artifact != null ? m.artifact.getContent() : m.content;
        return MemoryRecord.create(
                UUID.fromString(m.artifactId), agentId, null,
                MemoryType.EPISODIC, content != null ? content : "", meta, Instant.now());
    }
}
