package com.openclaw.memory.storage;

import com.openclaw.memory.blackboard.Artifact;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forgetting System - Three-tier memory decay and compression.
 * 
 * Tier 1: Working Memory Eviction (LRU + salience)
 * Tier 2: Semantic Compression (summarization)
 * Tier 3: Cold Archive (Parquet compression)
 * 
 * Never deletes. Always archives.
 */
@Slf4j
public class ForgetSystem {
    
    // Tier 1: Working memory with LRU eviction
    private final LinkedHashMap<String, WorkingMemoryEntry> workingMemory;
    private final int tier1MaxSize;
    
    // Tier 2: Compressed semantic memory
    private final ConcurrentHashMap<String, CompressedMemory> tier2Storage;
    private final int tier2MaxSize;
    
    // Tier 3: Cold archive
    private final ConcurrentHashMap<String, ArchivedMemory> tier3Storage;
    
    // Salience tracker
    private final Map<String, SalienceInfo> salienceMap = new ConcurrentHashMap<>();
    
    private final SemanticCompressor compressor;
    private final ArchiveWriter archiveWriter;
    private final SalienceCalculator salienceCalculator;
    
    public ForgetSystem(int tier1Size, int tier2Size, 
                       SemanticCompressor compressor,
                       ArchiveWriter writer,
                       SalienceCalculator salienceCalc) {
        this.tier1MaxSize = tier1Size;
        this.tier2MaxSize = tier2Size;
        this.compressor = compressor;
        this.archiveWriter = writer;
        this.salienceCalculator = salienceCalc;
        
        // LRU map
        this.workingMemory = new LinkedHashMap<String, WorkingMemoryEntry>(tier1Size, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, WorkingMemoryEntry> eldest) {
                if (size() > tier1MaxSize) {
                    promoteToTier2(eldest.getValue());
                    return true;
                }
                return false;
            }
        };
        
        this.tier2Storage = new ConcurrentHashMap<>(tier2Size);
        this.tier3Storage = new ConcurrentHashMap<>();
    }
    
    /**
     * Add memory to working memory (Tier 1)
     */
    public void remember(Artifact artifact) {
        String id = artifact.getArtifactId();
        
        WorkingMemoryEntry entry = new WorkingMemoryEntry(
            artifact,
            LocalDateTime.now(),
            1  // Initial access count
        );
        
        workingMemory.put(id, entry);
        salienceMap.put(id, new SalienceInfo(id, 1.0)); // Initial high salience
        
        log.debug("Added to Tier 1 (working memory): {}", id);
    }
    
    /**
     * Access memory (updates salience)
     */
    public Optional<Artifact> access(String memoryId) {
        // Try Tier 1 first
        WorkingMemoryEntry tier1 = workingMemory.get(memoryId);
        if (tier1 != null) {
            tier1.accessCount++;
            tier1.lastAccessed = LocalDateTime.now();
            updateSalience(memoryId, 1.0); // Boost salience on access
            return Optional.of(tier1.artifact);
        }
        
        // Try Tier 2
        CompressedMemory tier2 = tier2Storage.get(memoryId);
        if (tier2 != null) {
            tier2.accessCount++;
            tier2.lastAccessed = LocalDateTime.now();
            updateSalience(memoryId, 0.5); // Lower boost for Tier 2
            log.debug("Promoted from Tier 2 to Tier 1: {}", memoryId);
            
            // Decompress and promote back to Tier 1
            Artifact decompressed = compressor.decompress(tier2);
            remember(decompressed);
            return Optional.of(decompressed);
        }
        
        // Try Tier 3 (cold archive)
        ArchivedMemory tier3 = tier3Storage.get(memoryId);
        if (tier3 != null) {
            tier3.accessCount++;
            tier3.lastAccessed = LocalDateTime.now();
            updateSalience(memoryId, 0.2); // Minimal boost for Tier 3
            log.info("Warm-read from Tier 3 (cold archive): {}", memoryId);
            
            // Decompress and promote
            Artifact restored = archiveWriter.read(tier3.archiveReference);
            remember(restored);
            return Optional.of(restored);
        }
        
        return Optional.empty();
    }
    
    /**
     * Get memory without triggering promotion
     */
    public Optional<Artifact> peek(String memoryId) {
        WorkingMemoryEntry entry = workingMemory.get(memoryId);
        if (entry != null) {
            return Optional.of(entry.artifact);
        }
        
        CompressedMemory compressed = tier2Storage.get(memoryId);
        if (compressed != null) {
            return Optional.of(compressor.decompress(compressed));
        }
        
        ArchivedMemory archived = tier3Storage.get(memoryId);
        if (archived != null) {
            return Optional.of(archiveWriter.read(archived.archiveReference));
        }
        
        return Optional.empty();
    }
    
    /**
     * Run forgetting/compression cycle
     */
    public ForgetCycleResult runForgetCycle(int percentileThreshold) {
        log.info("Running forget cycle with percentile threshold: {}", percentileThreshold);
        
        ForgetCycleResult result = new ForgetCycleResult();
        LocalDateTime now = LocalDateTime.now();
        
        // Tier 1 → Tier 2 promotion based on salience and age
        List<String> tier1Candidates = new ArrayList<>(workingMemory.keySet());
        for (String memoryId : tier1Candidates) {
            WorkingMemoryEntry entry = workingMemory.get(memoryId);
            if (entry == null) continue;
            
            SalienceInfo salience = salienceMap.get(memoryId);
            long ageSeconds = java.time.temporal.ChronoUnit.SECONDS
                .between(entry.created, now);
            
            // Decay salience over time
            double currentSalience = salience.score * Math.exp(-0.001 * ageSeconds);
            
            if (currentSalience < (100 - percentileThreshold) / 100.0) {
                promoteToTier2(entry);
                result.movedToTier2++;
            }
        }
        
        // Tier 2 → Tier 3 archival if tier2 is full
        if (tier2Storage.size() > tier2MaxSize) {
            List<CompressedMemory> tier2Mems = new ArrayList<>(tier2Storage.values());
            tier2Mems.sort(Comparator.comparingDouble(m -> 
                salienceMap.getOrDefault(m.originalId, new SalienceInfo(m.originalId, 0)).score
            ));
            
            for (CompressedMemory compressed : tier2Mems) {
                if (tier2Storage.size() <= tier2MaxSize * 0.8) break;
                
                promoteToTier3(compressed);
                result.movedToTier3++;
            }
        }
        
        result.timestamp = now;
        log.info("Forget cycle completed: {} → Tier2, {} → Tier3", 
                result.movedToTier2, result.movedToTier3);
        
        return result;
    }
    
    /**
     * Promote from Tier 1 to Tier 2
     */
    private void promoteToTier2(WorkingMemoryEntry entry) {
        CompressedMemory compressed = new CompressedMemory(
            entry.artifact.getArtifactId(),
            compressor.compress(entry.artifact),
            entry.accessCount,
            LocalDateTime.now()
        );
        
        tier2Storage.put(entry.artifact.getArtifactId(), compressed);
        log.debug("Promoted to Tier 2 (semantic compression): {}", entry.artifact.getArtifactId());
    }
    
    /**
     * Promote from Tier 2 to Tier 3
     */
    private void promoteToTier3(CompressedMemory compressed) {
        Artifact decompressed = compressor.decompress(compressed);
        String archiveRef = archiveWriter.archive(decompressed, compressed.originalId);
        
        ArchivedMemory archived = new ArchivedMemory(
            compressed.originalId,
            archiveRef,
            compressed.accessCount,
            LocalDateTime.now()
        );
        
        tier3Storage.put(compressed.originalId, archived);
        tier2Storage.remove(compressed.originalId);
        
        log.info("Archived to Tier 3 (cold storage): {}", compressed.originalId);
    }
    
    private void updateSalience(String memoryId, double boost) {
        salienceMap.compute(memoryId, (k, v) -> {
            if (v == null) {
                return new SalienceInfo(memoryId, boost);
            }
            v.score = Math.min(1.0, v.score + boost * 0.1);
            v.lastUpdated = LocalDateTime.now();
            return v;
        });
    }
    
    // ===== Data Models =====
    
    @Data
    public static class WorkingMemoryEntry {
        public Artifact artifact;
        public LocalDateTime created;
        public LocalDateTime lastAccessed;
        public long accessCount;
        
        public WorkingMemoryEntry(Artifact artifact, LocalDateTime created, long accessCount) {
            this.artifact = artifact;
            this.created = created;
            this.lastAccessed = created;
            this.accessCount = accessCount;
        }
    }
    
    @Data
    public static class CompressedMemory {
        public String originalId;
        public String compressed;
        public long accessCount;
        public LocalDateTime lastAccessed;
        
        public CompressedMemory(String id, String compressed, long accessCount, LocalDateTime accessed) {
            this.originalId = id;
            this.compressed = compressed;
            this.accessCount = accessCount;
            this.lastAccessed = accessed;
        }
    }
    
    @Data
    public static class ArchivedMemory {
        public String originalId;
        public String archiveReference;
        public long accessCount;
        public LocalDateTime lastAccessed;
        
        public ArchivedMemory(String id, String ref, long accessCount, LocalDateTime accessed) {
            this.originalId = id;
            this.archiveReference = ref;
            this.accessCount = accessCount;
            this.lastAccessed = accessed;
        }
    }
    
    @Data
    public static class SalienceInfo {
        public String memoryId;
        public double score;
        public LocalDateTime lastUpdated;
        
        public SalienceInfo(String id, double score) {
            this.memoryId = id;
            this.score = score;
            this.lastUpdated = LocalDateTime.now();
        }
    }
    
    @Data
    public static class ForgetCycleResult {
        public int movedToTier2;
        public int movedToTier3;
        public LocalDateTime timestamp;
    }
    
    // ===== Interfaces =====
    
    public interface SemanticCompressor {
        String compress(Artifact artifact);
        Artifact decompress(CompressedMemory compressed);
    }
    
    public interface ArchiveWriter {
        String archive(Artifact artifact, String memoryId);
        Artifact read(String archiveReference);
    }
    
    public interface SalienceCalculator {
        double calculate(Artifact artifact);
    }
}
