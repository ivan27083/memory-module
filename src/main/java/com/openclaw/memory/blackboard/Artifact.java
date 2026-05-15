package com.openclaw.memory.blackboard;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Artifact Contract - базовый контракт для всех выходов системы.
 */
public class Artifact {

    private final String artifactId;
    private final String producedBy;
    private final List<String> dependsOn;
    private final ArtifactType type;
    private final String contentType;
    private final LocalDateTime timestamp;
    private final Provenance provenance;
    private final String content;
    private final Map<String, Object> contentMap;
    private final Map<String, Object> metadata;
    private final String version;
    private final boolean immutable;

    public enum ArtifactType {
        EVENT,
        MEMORY,
        GRAPH,
        INDEX,
        REPORT,
        CONFLICT,
        WORKING_STATE
    }

    public Artifact(Builder builder) {
        this.artifactId = Objects.requireNonNull(builder.artifactId, "artifactId");
        this.producedBy = Objects.requireNonNull(builder.producedBy, "producedBy");
        this.dependsOn = Collections.unmodifiableList(new ArrayList<>(builder.dependsOn));
        this.type = Objects.requireNonNull(builder.type, "type");
        this.contentType = builder.contentType;
        this.timestamp = builder.timestamp;
        this.provenance = Objects.requireNonNull(builder.provenance, "provenance");
        this.content = builder.content;
        this.contentMap = Collections.unmodifiableMap(new LinkedHashMap<>(builder.contentMap));
        this.metadata = Collections.synchronizedMap(new LinkedHashMap<>(builder.metadata));
        this.version = builder.version;
        this.immutable = builder.immutable;
    }

    public Artifact(String artifactId, String content, String contentType,
                    LocalDateTime timestamp, Provenance provenance,
                    Map<String, Object> metadata) {
        this(new Builder()
                .artifactId(artifactId)
                .producedBy(provenance == null ? "unknown" : provenance.getSourceAgent())
                .type(ArtifactType.MEMORY)
                .contentText(content)
                .contentType(contentType)
                .timestamp(timestamp)
                .provenance(provenance)
                .metadata(metadata));
    }

    public String getArtifactId() { return artifactId; }
    public String getProducedBy() { return producedBy; }
    public List<String> getDependsOn() { return dependsOn; }
    public ArtifactType getType() { return type; }
    public String getContentType() { return contentType; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Provenance getProvenance() { return provenance; }
    public String getContent() { return content; }
    public Map<String, Object> getContentMap() { return contentMap; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getVersion() { return version; }
    public boolean isImmutable() { return immutable; }

    public static class Builder {
        private String artifactId;
        private String producedBy;
        private List<String> dependsOn = new ArrayList<>();
        private ArtifactType type;
        private String contentType = "text";
        private LocalDateTime timestamp = LocalDateTime.now();
        private Provenance provenance;
        private String content = "";
        private Map<String, Object> contentMap = new LinkedHashMap<>();
        private Map<String, Object> metadata = new HashMap<>();
        private String version = "1.0";
        private boolean immutable = true;

        public Builder artifactId(String id) { this.artifactId = id; return this; }
        public Builder producedBy(String agent) { this.producedBy = agent; return this; }
        public Builder dependsOn(List<String> deps) { this.dependsOn = deps == null ? new ArrayList<>() : new ArrayList<>(deps); return this; }
        public Builder type(ArtifactType t) { this.type = t; return this; }
        public Builder contentType(String t) { this.contentType = t == null ? "text" : t; return this; }
        public Builder timestamp(LocalDateTime ts) { this.timestamp = ts == null ? LocalDateTime.now() : ts; return this; }
        public Builder timestamp(Instant ts) { this.timestamp = ts == null ? LocalDateTime.now() : LocalDateTime.ofInstant(ts, ZoneOffset.UTC); return this; }
        public Builder provenance(Provenance prov) { this.provenance = prov; return this; }
        public Builder contentText(String text) { this.content = text == null ? "" : text; return this; }
        public Builder content(Map<String, Object> cont) {
            this.contentMap = cont == null ? new LinkedHashMap<>() : new LinkedHashMap<>(cont);
            this.content = this.contentMap.toString();
            return this;
        }
        public Builder metadata(Map<String, Object> meta) { this.metadata = meta == null ? new HashMap<>() : new HashMap<>(meta); return this; }
        public Builder version(String v) { this.version = v; return this; }
        public Builder immutable(boolean imm) { this.immutable = imm; return this; }

        public Artifact build() {
            if (provenance == null) {
                provenance = new Provenance.Builder()
                        .provenanceId(artifactId == null ? UUID.randomUUID().toString() : artifactId)
                        .sourceAgent(producedBy == null ? "unknown" : producedBy)
                        .timestamp(timestamp)
                        .build();
            }
            if (producedBy == null) {
                producedBy = provenance.getSourceAgent();
            }
            if (type == null) {
                type = ArtifactType.MEMORY;
            }
            return new Artifact(this);
        }
    }

    @Override
    public String toString() {
        return "Artifact{" +
                "id='" + artifactId + '\'' +
                ", producer='" + producedBy + '\'' +
                ", type=" + type +
                ", timestamp=" + timestamp +
                ", version='" + version + '\'' +
                '}';
    }
}
