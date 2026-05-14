package com.openclaw.memory.agents.architect;

import com.openclaw.memory.agents.BaseAgent;
import com.openclaw.memory.blackboard.*;
import java.util.List;

/**
 * Architect Agent Interface
 * 
 * Ответственность:
 * - Определяет инварианты системы
 * - Контролирует границы архитектуры
 * - Валидирует корректность разложения
 */
public interface ArchitectAgent extends BaseAgent {
    
    /**
     * Определить системные инварианты
     */
    List<SystemInvariant> defineSystemInvariants();
    
    /**
     * Проверить, соответствует ли задача архитектурным границам
     */
    boolean conformsToArchitecture(Task task);
    
    /**
     * Проверить, соответствует ли артефакт схеме
     */
    boolean validateArtifactSchema(Artifact artifact);
    
    /**
     * Валидировать разложение задач
     */
    DecompositionValidation validateDecomposition(List<Task> decomposed, String originalTask);
    
    /**
     * Получить архитектурный отчет
     */
    ArchitectureReport getArchitectureReport();
    
    class SystemInvariant {
        public final String invariantId;
        public final String description;
        public final String validationRule;
        public final String severity;
        
        public SystemInvariant(String id, String desc, String rule, String severity) {
            this.invariantId = id;
            this.description = desc;
            this.validationRule = rule;
            this.severity = severity;
        }
    }
    
    class DecompositionValidation {
        public final boolean valid;
        public final List<String> issues;
        public final List<String> warnings;
        public final double completenessScore;
        
        public DecompositionValidation(boolean valid, List<String> issues,
                                     List<String> warnings, double score) {
            this.valid = valid;
            this.issues = issues;
            this.warnings = warnings;
            this.completenessScore = score;
        }
    }
    
    class ArchitectureReport {
        public final String reportId;
        public final List<String> definedInvariants;
        public final List<String> violatedInvariants;
        public final List<String> boundaryViolations;
        public final double conformanceScore;
        
        public ArchitectureReport(String id, List<String> invariants,
                                List<String> violations, List<String> boundaries,
                                double score) {
            this.reportId = id;
            this.definedInvariants = invariants;
            this.violatedInvariants = violations;
            this.boundaryViolations = boundaries;
            this.conformanceScore = score;
        }
    }
}
