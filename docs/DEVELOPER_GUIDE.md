# Memory Module — Developer Quick Reference

## 🗺️ Project Structure

```
src/main/java/com/openclaw/memory/
├── retrieval/
│   └── QMDRetrievalEngine.java          # Hybrid search (BM25+Vector+Graph)
├── working_memory/
│   └── WorkingMemoryComposer.java       # Context reconstruction
├── graph/
│   └── TemporalGraphManager.java        # Causal graph with temporal windows
├── storage/
│   └── ForgetSystem.java                # 3-tier memory management
├── indexing/
│   └── IncrementalIndexingEngine.java   # DAG pipeline with caching
├── multimodal/
│   └── MultimodalProcessor.java         # Text/Code/Image/Log processing
├── agents/
│   ├── conflict/
│   │   └── ConflictResolutionSystem.java # Contradiction detection
│   ├── observability/
│   │   └── ObservabilitySystem.java      # Prometheus metrics
│   └── ... (12 agent types)
├── mcp/
│   └── MCPMemoryTools.java              # 9 memory tools for agents
├── blackboard/
│   └── MemoryBlackboard.java            # Central messaging hub
├── application/
│   ├── MemoryFacade.java                # Main API
│   └── DefaultMemoryFacade.java         # Implementation
└── config/
    └── MemoryModuleConfiguration.java   # Spring configuration
```

---

## 🔍 Quick API Reference

### 1. QMD Retrieval (Hybrid Search)

```java
// Initialize
QMDRetrievalEngine engine = new QMDRetrievalEngine(
    bm25Retriever, vectorRetriever, graphRetriever, 
    reranker, queryDecomposer
);

// Search
QMDRetrievalEngine.RetrievalOptions opts = 
    new QMDRetrievalEngine.RetrievalOptions();
opts.topK = 100;    // Candidates
opts.topN = 10;     // Final results

QMDRetrievalEngine.RetrievalResults results = 
    engine.retrieve("What happened last week?", opts);

// Results
for (RankedCandidate candidate : results.results) {
    System.out.println("Score: " + candidate.finalScore);
    System.out.println("RRF: " + candidate.rrfScore);
    System.out.println("Content: " + candidate.artifact.getContent());
}
```

---

### 2. Temporal Graph (Causal Reasoning)

```java
// Initialize
TemporalGraphManager graph = new TemporalGraphManager();

// Add nodes
TemporalGraphManager.TemporalNode node = 
    graph.addNode("entity_1", NodeType.ENTITY, data);

// Add edges with temporal validity
TemporalGraphManager.TemporalEdge edge = graph.addEdge(
    "from", "to", 
    EdgeType.CAUSES,
    validFrom: LocalDateTime.now(),
    validTo:   LocalDateTime.now().plus(30, ChronoUnit.DAYS),
    confidence: 0.95
);

// Query at specific time
List<String> dependencies = graph.traverse(
    "node_id", 
    atTime: LocalDateTime.now(),
    TraversalType.BACKWARD,
    maxDepth: 5
);

// Get causal chain
TemporalGraphManager.CausalChain chain = 
    graph.getCausalChain("node_id", LocalDateTime.now());
System.out.println("Dependencies: " + chain.dependencies);
System.out.println("Depth: " + chain.depth);

// Check consistency
TemporalGraphManager.GraphConsistencyReport report = 
    graph.validateConsistency(LocalDateTime.now());
System.out.println("Consistent: " + report.isConsistent());
```

---

### 3. Working Memory Composer (Context)

```java
// Initialize
WorkingMemoryComposer composer = new WorkingMemoryComposer(
    retrievalEngine, conflictResolver, temporalResolver,
    maxContextTokens: 8000
);

// Compose context
WorkingMemoryComposer.CompositionOptions opts = 
    new WorkingMemoryComposer.CompositionOptions();
opts.maxMemoriesPerContext = 20;
opts.maxCandidates = 100;
opts.confidenceThreshold = 0.5;
opts.includeExplanation = true;

WorkingMemoryComposer.WorkingMemoryContext ctx = 
    composer.compose("Tell me about the project", opts);

// Output
System.out.println("Query: " + ctx.originalQuery);
System.out.println("Selected: " + ctx.selectedMemories.size());
System.out.println("\nComposed Prompt:\n" + ctx.composedPrompt);
System.out.println("\nCausal Dependencies:");
ctx.causalChains.forEach((k, v) -> 
    System.out.println("  " + k + " → " + v)
);
```

---

### 4. Forgetting System (3-Tier Memory)

```java
// Initialize
ForgetSystem forget = new ForgetSystem(
    tier1Size: 100,
    tier2Size: 1000,
    compressor, archiveWriter, salienceCalculator
);

// Add memory
forget.remember(artifact);

// Access (with auto-promotion)
Optional<Artifact> mem = forget.access("memory_id");

// Peek (without promotion)
Optional<Artifact> peeked = forget.peek("memory_id");

// Run forget cycle
ForgetSystem.ForgetCycleResult result = 
    forget.runForgetCycle(percentileThreshold: 25);
System.out.println("Moved to Tier2: " + result.movedToTier2);
System.out.println("Moved to Tier3: " + result.movedToTier3);
```

---

### 5. Incremental Indexing (DAG)

```java
// Initialize
IncrementalIndexingEngine engine = 
    new IncrementalIndexingEngine(parallelism: 4);

// Register pipeline stages
engine.registerNode("normalize", new NormalizationNode());
engine.registerNode("chunk", new ChunkingNode());
engine.registerNode("embed", new EmbeddingNode());
engine.registerNode("index", new IndexingNode());
engine.registerNode("graph_update", new GraphUpdateNode());

// Index single artifact
IncrementalIndexingEngine.IndexingResult result = 
    engine.executeIndexing(artifact);
System.out.println("Cached: " + result.cached);
System.out.println("Time: " + result.totalTimeMs + "ms");

// Batch processing
List<Artifact> artifacts = ...;
List<IncrementalIndexingEngine.IndexingResult> results = 
    engine.batchIndexing(artifacts);

// Cache stats
IncrementalIndexingEngine.CacheStatistics stats = 
    engine.getStatistics();
System.out.println("Cache entries: " + stats.totalCacheEntries);
System.out.println("Memory: " + stats.totalMemoryBytes + " bytes");
```

---

### 6. Conflict Resolution

```java
// Initialize
ConflictResolutionSystem system = new ConflictResolutionSystem();

// Detect conflicts
List<WorkingMemoryComposer.SelectedMemory> memories = ...;
List<ConflictResolutionSystem.DetectedConflict> conflicts = 
    system.detectConflicts(memories);

// Resolve
List<WorkingMemoryComposer.SelectedMemory> resolved = 
    system.resolve(memories, "context");

// Belief revision
system.reviseMemory(oldMemory, newMemory, 
    "API changed in v2.0");

// Get revision history
Optional<ConflictResolutionSystem.BeliefRevisionHistory> hist = 
    system.getRevisionHistory("memory_id");
hist.ifPresent(h -> 
    h.revisions.forEach(r -> 
        System.out.println(r.reason + " at " + r.timestamp)
    )
);

// Confidence scoring
double confidence = system.scoreConfidence(memories, 
    "Java version >= 21");
```

---

### 7. Multimodal Processing

```java
// Initialize
MultimodalProcessor processor = new MultimodalProcessor(
    textProcessor, codeProcessor, imageProcessor, logProcessor
);

// Process single modality
MultimodalProcessor.ProcessedContent text = 
    processor.process("Hello world", ContentType.TEXT);

// Analyze multiple modalities
MultimodalProcessor.MultimodalInput input = 
    new MultimodalProcessor.MultimodalInput();
input.id = "doc_1";
input.text = "The system uses Java";
input.code = "public class Main { ... }";
input.images = Arrays.asList("path/to/image.png");
input.logs = "2026-05-14 INFO: System started";

MultimodalProcessor.MultimodalAnalysis analysis = 
    processor.analyzeMultimodal(input);

// Fused embedding
double[] embedding = analysis.fusedEmbedding;  // 768-dim
System.out.println("Embedding dim: " + embedding.length);
```

---

### 8. Observability

```java
// Initialize
MeterRegistry registry = new SimpleMeterRegistry();
ObservabilitySystem observability = 
    new ObservabilitySystem(registry);

// Record operations
observability.recordRetrieval(durationMs: 45, cacheHit: true);
observability.recordIndexing(durationMs: 0.8, itemsIndexed: 1);
observability.recordComposition(durationMs: 200, memoriesSelected: 15);
observability.recordConflictResolution(durationMs: 10, conflictsResolved: 2);

// Record agent execution
observability.recordAgentExecution("retrieval", durationMs: 150, success: true);

// Update memory stats
observability.updateMemoryStats(tier1: 98, tier2: 342, tier3: 8921);

// Get reports
double cacheHitRatio = observability.getCacheHitRatio();
ObservabilitySystem.LatencyStats latency = 
    observability.getRetrievalLatencyStats();
ObservabilitySystem.SystemHealthReport health = 
    observability.getHealthReport();

System.out.println("Cache hit: " + cacheHitRatio);
System.out.println("Health: " + (health.isHealthy() ? "✓" : "✗"));
```

---

### 9. MCP Memory Tools

```java
// Initialize
MCPMemoryTools tools = new MCPMemoryTools(implementation);

// Search
MCPMemoryTools.MemorySearchResult search = tools.search(
    "What is the project status?",
    new MCPMemoryTools.SearchOptions()
);

// Store
MCPMemoryTools.MemoryStoreResult stored = tools.store(
    "Java 21 with Spring Boot 3.3.5",
    "TECHNICAL_NOTE",
    "architect_agent"
);

// Update
MCPMemoryTools.MemoryUpdateResult updated = tools.update(
    stored.memoryId,
    "Java 21+ with Spring Boot 3.3.5+",
    "Version upgrade"
);

// Timeline
MCPMemoryTools.TimelineResult timeline = tools.timeline(
    "project",
    LocalDateTime.now().minus(30, ChronoUnit.DAYS),
    LocalDateTime.now()
);

// Conflicts
MCPMemoryTools.ConflictsResult conflicts = tools.getConflicts();
System.out.println("Active conflicts: " + conflicts.activeConflicts);

// Forget cycle
MCPMemoryTools.ForgetResult forget = tools.forget(percentileThreshold: 25);
System.out.println("Processed: " + forget.processedCount);

// Statistics
MCPMemoryTools.StatisticsResult stats = tools.statistics();
System.out.println("Total memories: " + stats.totalMemories);
System.out.println("Cache ratio: " + stats.cacheHitRatio);
```

---

## 🧪 Common Patterns

### Pattern 1: Full Retrieval & Composition

```java
// 1. Search
QMDRetrievalEngine.RetrievalResults retrieved = 
    retrievalEngine.retrieve(userQuery, options);

// 2. Compose context
WorkingMemoryComposer.WorkingMemoryContext context = 
    composer.compose(userQuery, opts);

// 3. Use for agent execution
String finalPrompt = context.composedPrompt;
```

### Pattern 2: Incremental Update Flow

```java
// 1. Store new fact
Artifact stored = tools.store(content, type, agent);

// 2. Index it
IndexingResult indexed = indexingEngine.executeIndexing(stored);

// 3. Add to graph
graph.addNode(stored.getArtifactId(), NodeType.FACT, stored);

// 4. Check consistency
GraphConsistencyReport report = graph.validateConsistency(now);
```

### Pattern 3: Conflict Resolution Flow

```java
// 1. Detect conflicts
List<DetectedConflict> conflicts = 
    conflictSystem.detectConflicts(memories);

// 2. Resolve
List<SelectedMemory> resolved = 
    conflictSystem.resolve(memories, context);

// 3. Record belief revisions
for (DetectedConflict c : conflicts) {
    conflictSystem.reviseMemory(old, new, reason);
}
```

### Pattern 4: Monitoring Loop

```java
// Periodic stats collection
ScheduledExecutorService scheduler = 
    Executors.newScheduledThreadPool(1);

scheduler.scheduleAtFixedRate(() -> {
    SystemHealthReport report = observability.getHealthReport();
    if (!report.isHealthy()) {
        logger.warn("System health degraded");
    }
}, 0, 60, TimeUnit.SECONDS);
```

---

## 🔧 Configuration

### application.yml

```yaml
memory:
  tier1:
    size: 100
  tier2:
    size: 1000
  retrieval:
    timeout-ms: 300
    confidence-threshold: 0.5
  composition:
    max-memories: 20
    max-tokens: 8000
  indexing:
    parallelism: 4
    cache-ttl-minutes: 60
```

### Spring Beans

```java
@Configuration
public class MemoryModuleConfiguration {
    
    @Bean
    public QMDRetrievalEngine qmdEngine(...) {
        return new QMDRetrievalEngine(...);
    }
    
    @Bean
    public WorkingMemoryComposer composer(...) {
        return new WorkingMemoryComposer(...);
    }
    
    @Bean
    public TemporalGraphManager graph(...) {
        return new TemporalGraphManager();
    }
    
    // ... more beans
}
```

---

## 📊 Debugging Tips

### Check Performance
```java
long startTime = System.nanoTime();
// ... operation
long durationMs = (System.nanoTime() - startTime) / 1_000_000;
System.out.println("Duration: " + durationMs + "ms");
```

### Inspect Retrieval Explanation
```java
RetrievalResults results = engine.retrieve(query, opts);
RetrievalExplanation exp = results.explanation;
System.out.println("Query morphology: " + exp.queryMorphology);
System.out.println("Fused results: " + exp.fusedResults.size());
```

### Check Memory Tiers
```java
CacheStatistics stats = indexingEngine.getStatistics();
System.out.println("Cache: " + stats.totalCacheEntries + " entries");

observability.updateMemoryStats(t1, t2, t3);
SystemHealthReport health = observability.getHealthReport();
System.out.println("T1: " + health.tier1Size + " items");
System.out.println("T2: " + health.tier2Size + " items");
System.out.println("T3: " + health.tier3Size + " items");
```

---

## 📚 Resources

- **Full Spec**: [docs/IMPLEMENTATION_v2.md](../docs/IMPLEMENTATION_v2.md)
- **README**: [README.md](../README.md)
- **Architecture**: [docs/ARCHITECTURE_RU.md](../docs/ARCHITECTURE_RU.md)

---

**Version:** 2.0.0  
**Last Updated:** 2026-05-14
