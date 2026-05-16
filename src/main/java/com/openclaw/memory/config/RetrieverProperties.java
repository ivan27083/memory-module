package com.openclaw.memory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "memory.module.agents.retrieval")
public class RetrieverProperties {

    private String provider = "qmd";
    private boolean cacheEnabled = true;
    private int cacheTtlMinutes = 60;
    private boolean bm25Enabled = true;
    private boolean vectorEnabled = true;
    private boolean rerankEnabled = true;

    // getters + setters
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public boolean isCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }
    public int getCacheTtlMinutes() { return cacheTtlMinutes; }
    public void setCacheTtlMinutes(int cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }
    public boolean isBm25Enabled() { return bm25Enabled; }
    public void setBm25Enabled(boolean bm25Enabled) { this.bm25Enabled = bm25Enabled; }
    public boolean isVectorEnabled() { return vectorEnabled; }
    public void setVectorEnabled(boolean vectorEnabled) { this.vectorEnabled = vectorEnabled; }
    public boolean isRerankEnabled() { return rerankEnabled; }
    public void setRerankEnabled(boolean rerankEnabled) { this.rerankEnabled = rerankEnabled; }
}