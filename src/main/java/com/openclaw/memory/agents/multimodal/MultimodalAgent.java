package com.openclaw.memory.agents.multimodal;

import com.openclaw.memory.agents.BaseAgent;
import java.util.List;
import java.util.Map;

/**
 * Multimodal Agent Interface
 * 
 * Ответственность:
 * - Обрабатывает изображения, логи, код, документы
 * - Интегрирует CLIP, OCR, tree-sitter
 * - Создает унифицированное пространство эмбеддингов
 */
public interface MultimodalAgent extends BaseAgent {
    
    /**
     * Обработать текстовый документ
     */
    DocumentEmbedding processDocument(String documentPath);
    
    /**
     * Обработать изображение
     */
    ImageEmbedding processImage(String imagePath);
    
    /**
     * Обработать исходный код
     */
    CodeEmbedding processCode(String codePath);
    
    /**
     * Обработать логи
     */
    LogEmbedding processLogs(String logPath);
    
    /**
     * Получить мультимодальное эмбеддинг для запроса
     */
    float[] getMultimodalEmbedding(String query);
    
    /**
     * Получить похожие элементы разных модальностей
     */
    CrossModalResults findCrossModalSimilar(String query);
    
    class DocumentEmbedding {
        public final String documentId;
        public final String content;
        public final float[] embedding;
        public final List<String> entities;
        public final Map<String, Float> keywords;
        
        public DocumentEmbedding(String id, String content, float[] emb,
                               List<String> entities, Map<String, Float> keywords) {
            this.documentId = id;
            this.content = content;
            this.embedding = emb;
            this.entities = entities;
            this.keywords = keywords;
        }
    }
    
    class ImageEmbedding {
        public final String imageId;
        public final String imagePath;
        public final float[] embedding;
        public final List<String> detectedObjects;
        public final String ocrText;
        
        public ImageEmbedding(String id, String path, float[] emb,
                            List<String> objects, String ocr) {
            this.imageId = id;
            this.imagePath = path;
            this.embedding = emb;
            this.detectedObjects = objects;
            this.ocrText = ocr;
        }
    }
    
    class CodeEmbedding {
        public final String codeId;
        public final String codePath;
        public final float[] embedding;
        public final String language;
        public final List<String> functions;
        public final List<String> classes;
        
        public CodeEmbedding(String id, String path, float[] emb, String lang,
                           List<String> functions, List<String> classes) {
            this.codeId = id;
            this.codePath = path;
            this.embedding = emb;
            this.language = lang;
            this.functions = functions;
            this.classes = classes;
        }
    }
    
    class LogEmbedding {
        public final String logId;
        public final String logPath;
        public final float[] embedding;
        public final List<String> parsedEvents;
        public final List<String> anomalies;
        
        public LogEmbedding(String id, String path, float[] emb,
                          List<String> events, List<String> anomalies) {
            this.logId = id;
            this.logPath = path;
            this.embedding = emb;
            this.parsedEvents = events;
            this.anomalies = anomalies;
        }
    }
    
    class CrossModalResults {
        public final List<DocumentEmbedding> similarDocuments;
        public final List<ImageEmbedding> similarImages;
        public final List<CodeEmbedding> similarCode;
        
        public CrossModalResults(List<DocumentEmbedding> docs, List<ImageEmbedding> images,
                               List<CodeEmbedding> code) {
            this.similarDocuments = docs;
            this.similarImages = images;
            this.similarCode = code;
        }
    }
}
