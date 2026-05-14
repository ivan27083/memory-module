package com.openclaw.memory.agents.qa;

import com.openclaw.memory.agents.BaseAgent;
import com.openclaw.memory.blackboard.*;
import java.util.List;

/**
 * QA / Evaluation Agent Interface
 * 
 * Ответственность:
 * - Валидирует инварианты корректности
 * - Запускает регрессионные тесты + бенчмарки поиска
 * - Контролирует гейты приемки
 */
public interface QAAgent extends BaseAgent {
    
    /**
     * Валидировать инварианты системы
     */
    ValidationResult validateSystemInvariants();
    
    /**
     * Валидировать артефакт
     */
    ValidationResult validateArtifact(Artifact artifact);
    
    /**
     * Запустить набор тестов
     */
    TestResults runTests(String testSuite);
    
    /**
     * Запустить бенчмарк поиска
     */
    BenchmarkResults runRetrievalBenchmark();
    
    /**
     * Проверить, прошел ли артефакт гейт приемки
     */
    boolean passesAcceptanceGate(Artifact artifact);
    
    class ValidationResult {
        public final boolean valid;
        public final List<String> violations;
        public final List<String> warnings;
        public final double conformanceScore;
        
        public ValidationResult(boolean valid, List<String> violations,
                              List<String> warnings, double score) {
            this.valid = valid;
            this.violations = violations;
            this.warnings = warnings;
            this.conformanceScore = score;
        }
    }
    
    class TestResults {
        public final int totalTests;
        public final int passedTests;
        public final int failedTests;
        public final double successRate;
        public final List<String> failures;
        
        public TestResults(int total, int passed, int failed,
                         double success, List<String> failures) {
            this.totalTests = total;
            this.passedTests = passed;
            this.failedTests = failed;
            this.successRate = success;
            this.failures = failures;
        }
    }
    
    class BenchmarkResults {
        public final double averageRetrievalLatencyMs;
        public final double p99LatencyMs;
        public final double meanAveragePrecision;
        public final double recallAt10;
        public final long queriesPerSecond;
        
        public BenchmarkResults(double avgLatency, double p99, double map,
                              double recall, long qps) {
            this.averageRetrievalLatencyMs = avgLatency;
            this.p99LatencyMs = p99;
            this.meanAveragePrecision = map;
            this.recallAt10 = recall;
            this.queriesPerSecond = qps;
        }
    }
}
