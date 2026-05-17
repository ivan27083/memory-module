package com.openclaw.memory.working_memory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Computes a salience score for a SelectedMemory based on four signals:
 *   salience = 0.40 * relevance + 0.30 * recency + 0.20 * confidence + 0.10 * accessFrequency
 *
 * Recency uses exponential half-life decay (half-life = 7 days).
 * Confidence and accessCount are read from artifact / retrieval metadata.
 */
public class SalienceScorer {

    private static final double W_RELEVANCE  = 0.40;
    private static final double W_RECENCY    = 0.30;
    private static final double W_CONFIDENCE = 0.20;
    private static final double W_ACCESS     = 0.10;

    /** Half-life for recency decay: 7 days expressed in hours. */
    private static final double HALF_LIFE_HOURS = 168.0;

    public double score(WorkingMemoryComposer.SelectedMemory m, Instant now) {
        double relevance   = clamp(m.relevanceScore);
        double recency     = recencyScore(validFromOf(m), now);
        double confidence  = confidenceOf(m);
        double access      = accessScore(m);
        return W_RELEVANCE  * relevance
             + W_RECENCY    * recency
             + W_CONFIDENCE * confidence
             + W_ACCESS     * access;
    }

    // ── Signal extractors ─────────────────────────────────────────────────────

    private static double recencyScore(Instant validFrom, Instant now) {
        if (validFrom == null) return 0.5;
        long hours = ChronoUnit.HOURS.between(validFrom, now);
        if (hours < 0) hours = 0;
        return Math.exp(-Math.log(2) * hours / HALF_LIFE_HOURS);
    }

    static double confidenceOf(WorkingMemoryComposer.SelectedMemory m) {
        Object conf = metadataValue(m, "confidence");
        if (conf instanceof Number n) return clamp(n.doubleValue());
        return 0.5;
    }

    private static double accessScore(WorkingMemoryComposer.SelectedMemory m) {
        Object count = metadataValue(m, "accessCount");
        if (count instanceof Number n) return clamp(n.doubleValue() / 100.0);
        return 0.0;
    }

    static Instant validFromOf(WorkingMemoryComposer.SelectedMemory m) {
        Object vf = metadataValue(m, "validFrom");
        if (vf instanceof Instant i)  return i;
        if (vf instanceof String  s)  { try { return Instant.parse(s); } catch (Exception ignored) {} }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Object metadataValue(WorkingMemoryComposer.SelectedMemory m, String key) {
        if (m.artifact != null && m.artifact.getMetadata() != null) {
            Object v = m.artifact.getMetadata().get(key);
            if (v != null) return v;
        }
        if (m.retrievalExplanation != null && m.retrievalExplanation.metadata() != null) {
            return m.retrievalExplanation.metadata().get(key);
        }
        return null;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
