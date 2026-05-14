package com.openclaw.memory.blackboard;

import java.time.Instant;
import java.util.*;

/**
 * Artifact Contract - базовый контракт для всех выходов системы.
 * Каждый артефакт ДОЛЖЕН иметь провенанс и линеаж.
 */
public class Artifact {
    
    private final String artifactId;
    private final String producedBy;
    private final List<String> dependsOn;
    private final ArtifactType type;
    private final Instant timestamp;
    private final Provenance provenance;
    private final Map<String, Object> content;
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
        this.artifactId = builder.artifactId;
        this.producedBy = builder.producedBy;
        this.dependsOn = Collections.unmodifiableList(builder.dependsOn);
        this.type = builder.type;
        this.timestamp = builder.timestamp;
        this.provenance = builder.provenance;
        this.content = Collections.unmodifiableMap(builder.content);
        this.version = builder.version;
        this.immutable = builder.immutable;
    }
    
    public String getArtifactId() { return artifactId; }
    public String getProducedBy() { return producedBy; }
    public List<String> getDependsOn() { return dependsOn; }
    public ArtifactType getType() { return type; }
    public Instant getTimestamp() { return timestamp; }
    public Provenance getProvenance() { return provenance; }
    public Map<String, Object> getContent() { return content; }
    public String getVersion() { return version; }
    public boolean isImmutable() { return immutable; }
    
    public static class Builder {
        private String artifactId;
        private String producedBy;
        private List<String> dependsOn = new ArrayList<>();
        private ArtifactType type;
        private Instant timestamp = Instant.now();
        private Provenance provenance;
        private Map<String, Object> content = new HashMap<>();
        private String version = "1.0";
        private boolean immutable = true;
        
        public Builder artifactId(String id) { this.artifactId = id; return this; }
        public Builder producedBy(String agent) { this.producedBy = agent; return this; }
        public Builder dependsOn(List<String> deps) { this.dependsOn = deps; return this; }
        public Builder type(ArtifactType t) { this.type = t; return this; }
        public Builder timestamp(Instant ts) { this.timestamp = ts; return this; }
        public Builder provenance(Provenance prov) { this.provenance = prov; return this; }
        public Builder content(Map<String, Object> cont) { this.content = cont; return this; }
        public Builder version(String v) { this.version = v; return this; }
        public Builder immutable(boolean imm) { this.immutable = imm; return this; }
        
        public Artifact build() {
            if (artifactId == null || producedBy == null || type == null) {
                throw new IllegalArgumentException("Artifact ID, producer, and type are required");
            }
            if (provenance == null) {
                throw new IllegalArgumentException("Provenance is required for all artifacts");
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
