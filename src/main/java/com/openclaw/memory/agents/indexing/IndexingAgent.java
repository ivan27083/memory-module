package com.openclaw.memory.agents.indexing;

import com.openclaw.memory.agents.BaseAgent;
import java.util.List;

/**
 * Indexing Agent Interface (CocoIndex-style DAG)
 * 
 * Ответственность:
 * - Строит инкрементальные конвейеры
 * - Гарантирует частичное пересчитывание
 * - Поддерживает граф инвалидации кеша
 */
public interface IndexingAgent extends BaseAgent {
    
    /**
     * Создать индексный конвейер
     */
    IndexPipeline createPipeline(String pipelineId, List<PipelineStage> stages);
    
    /**
     * Добавить данные в конвейер
     */
    void addData(String pipelineId, String data);
    
    /**
     * Построить индекс (полный или инкрементальный)
     */
    BuildResult buildIndex(String pipelineId, boolean incremental);
    
    /**
     * Получить инвалидированные узлы
     */
    List<String> getInvalidatedNodes(String pipelineId);
    
    /**
     * Пересчитать только инвалидированные узлы
     */
    void recomputeInvalidated(String pipelineId);
    
    /**
     * Получить график зависимостей конвейера
     */
    DependencyDAG getDependencyDAG(String pipelineId);
    
    class PipelineStage {
        public final String stageName;
        public final String stageType; // normalize, chunk, embed, index, etc
        public final List<String> dependencies;
        public final String outputType;
        
        public PipelineStage(String name, String type, List<String> deps, String output) {
            this.stageName = name;
            this.stageType = type;
            this.dependencies = deps;
            this.outputType = output;
        }
    }
    
    class IndexPipeline {
        public final String pipelineId;
        public final List<PipelineStage> stages;
        public final int totalInputs;
        public final int processedInputs;
        
        public IndexPipeline(String id, List<PipelineStage> stages, int total, int processed) {
            this.pipelineId = id;
            this.stages = stages;
            this.totalInputs = total;
            this.processedInputs = processed;
        }
    }
    
    class BuildResult {
        public final String buildId;
        public final boolean success;
        public final long startTimeMs;
        public final long endTimeMs;
        public final int itemsProcessed;
        public final List<String> errors;
        
        public BuildResult(String id, boolean success, long start, long end,
                         int items, List<String> errors) {
            this.buildId = id;
            this.success = success;
            this.startTimeMs = start;
            this.endTimeMs = end;
            this.itemsProcessed = items;
            this.errors = errors;
        }
        
        public long getDurationMs() {
            return endTimeMs - startTimeMs;
        }
    }
    
    class DependencyDAG {
        public final int nodeCount;
        public final int edgeCount;
        public final List<String> nodes;
        public final List<Dependency> edges;
        
        public DependencyDAG(int nodes, int edges, List<String> nodeList,
                           List<Dependency> edgeList) {
            this.nodeCount = nodes;
            this.edgeCount = edges;
            this.nodes = nodeList;
            this.edges = edgeList;
        }
        
        public static class Dependency {
            public final String from;
            public final String to;
            public final boolean cached;
            
            public Dependency(String from, String to, boolean cached) {
                this.from = from;
                this.to = to;
                this.cached = cached;
            }
        }
    }
}
