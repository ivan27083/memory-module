package com.openclaw.memory.blackboard;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Provenance - отслеживает происхождение артефакта.
 */
public class Provenance {

    private final String provenanceId;
    private final String sourceAgent;
    private final List<String> sourceEventIds;
    private final LocalDateTime timestamp;
    private final float confidence;
    private final List<String> lineage;
    private final Map<String, Object> metadata;
    private volatile String supersededBy;

    public Provenance(Builder builder) {
        this.provenanceId = builder.provenanceId;
        this.sourceAgent = builder.sourceAgent;
        this.sourceEventIds = Collections.unmodifiableList(new ArrayList<>(builder.sourceEventIds));
        this.timestamp = builder.timestamp;
        this.confidence = builder.confidence;
        this.lineage = Collections.unmodifiableList(new ArrayList<>(builder.lineage));
        this.metadata = Collections.synchronizedMap(new LinkedHashMap<>(builder.metadata));
        this.supersededBy = builder.supersededBy;

        if (confidence < 0f || confidence > 1f) {
            throw new IllegalArgumentException("Confidence must be between 0 and 1");
        }
        if (sourceEventIds.isEmpty()) {
            throw new IllegalArgumentException("At least one source event ID is required");
        }
    }

    public Provenance(String provenanceId, String sourceAgent, List<String> sourceEventIds,
                      LocalDateTime timestamp, float confidence) {
        this(new Builder()
                .provenanceId(provenanceId)
                .sourceAgent(sourceAgent)
                .sourceEventIds(sourceEventIds)
                .timestamp(timestamp)
                .confidence(confidence));
    }

    public String getProvenanceId() { return provenanceId; }
    public String getSourceAgent() { return sourceAgent; }
    public List<String> getSourceEventIds() { return sourceEventIds; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public float getConfidence() { return confidence; }
    public float getConfidenceScore() { return confidence; }
    public List<String> getLineage() { return lineage; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Optional<String> getSupersededBy() { return Optional.ofNullable(supersededBy); }

    public Provenance markSuperseded(String artifactId) {
        this.supersededBy = artifactId;
        this.metadata.put("supersededBy", artifactId);
        this.metadata.put("supersededAt", LocalDateTime.now());
        return this;
    }

    public static class Builder {
        private String provenanceId = UUID.randomUUID().toString();
        private String sourceAgent = "unknown";
        private List<String> sourceEventIds = new ArrayList<>();
        private LocalDateTime timestamp = LocalDateTime.now();
        private float confidence = 1.0f;
        private List<String> lineage = new ArrayList<>();
        private Map<String, Object> metadata = new HashMap<>();
        private String supersededBy;

        public Builder provenanceId(String id) { this.provenanceId = id; return this; }
        public Builder sourceAgent(String agent) { this.sourceAgent = agent; return this; }
        public Builder sourceEventIds(List<String> ids) {
            this.sourceEventIds = ids == null ? new ArrayList<>() : new ArrayList<>(ids);
            return this;
        }
        public Builder addSourceEventId(String id) { this.sourceEventIds.add(id); return this; }
        public Builder timestamp(LocalDateTime ts) { this.timestamp = ts == null ? LocalDateTime.now() : ts; return this; }
        public Builder confidence(float conf) { this.confidence = conf; return this; }
        public Builder lineage(List<String> line) { this.lineage = line == null ? new ArrayList<>() : new ArrayList<>(line); return this; }
        public Builder addLineage(String item) { this.lineage.add(item); return this; }
        public Builder metadata(Map<String, Object> meta) { this.metadata = meta == null ? new HashMap<>() : new HashMap<>(meta); return this; }
        public Builder putMetadata(String key, Object value) { this.metadata.put(key, value); return this; }
        public Builder supersededBy(String id) { this.supersededBy = id; return this; }

        public Provenance build() {
            if (sourceEventIds.isEmpty()) {
                sourceEventIds.add(provenanceId);
            }
            return new Provenance(this);
        }
    }

    @Override
    public String toString() {
        return "Provenance{" +
                "sourceAgent='" + sourceAgent + '\'' +
                ", sources=" + sourceEventIds.size() +
                ", confidence=" + confidence +
                ", lineageDepth=" + lineage.size() +
                '}';
    }
}
