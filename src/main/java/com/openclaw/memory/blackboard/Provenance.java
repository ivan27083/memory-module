package com.openclaw.memory.blackboard;

import java.util.*;

/**
 * Provenance - отслеживает происхождение артефакта
 */
public class Provenance {
    
    private final List<String> sourceEventIds;
    private final float confidence;
    private final List<String> lineage;
    private final Map<String, Object> metadata;
    
    public Provenance(Builder builder) {
        this.sourceEventIds = Collections.unmodifiableList(builder.sourceEventIds);
        this.confidence = builder.confidence;
        this.lineage = Collections.unmodifiableList(builder.lineage);
        this.metadata = Collections.unmodifiableMap(builder.metadata);
        
        // Validate confidence
        if (confidence < 0f || confidence > 1f) {
            throw new IllegalArgumentException("Confidence must be between 0 and 1");
        }
    }
    
    public List<String> getSourceEventIds() { return sourceEventIds; }
    public float getConfidence() { return confidence; }
    public List<String> getLineage() { return lineage; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    public static class Builder {
        private List<String> sourceEventIds = new ArrayList<>();
        private float confidence = 1.0f;
        private List<String> lineage = new ArrayList<>();
        private Map<String, Object> metadata = new HashMap<>();
        
        public Builder sourceEventIds(List<String> ids) { 
            this.sourceEventIds = new ArrayList<>(ids); 
            return this; 
        }
        
        public Builder addSourceEventId(String id) { 
            this.sourceEventIds.add(id); 
            return this; 
        }
        
        public Builder confidence(float conf) { 
            this.confidence = conf; 
            return this; 
        }
        
        public Builder lineage(List<String> line) { 
            this.lineage = new ArrayList<>(line); 
            return this; 
        }
        
        public Builder addLineage(String item) { 
            this.lineage.add(item); 
            return this; 
        }
        
        public Builder metadata(Map<String, Object> meta) { 
            this.metadata = new HashMap<>(meta); 
            return this; 
        }
        
        public Builder putMetadata(String key, Object value) { 
            this.metadata.put(key, value); 
            return this; 
        }
        
        public Provenance build() {
            if (sourceEventIds.isEmpty()) {
                throw new IllegalArgumentException("At least one source event ID is required");
            }
            return new Provenance(this);
        }
    }
    
    @Override
    public String toString() {
        return "Provenance{" +
                "sources=" + sourceEventIds.size() +
                ", confidence=" + confidence +
                ", lineageDepth=" + lineage.size() +
                '}';
    }
}
