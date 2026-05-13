package com.openclaw.memory.domain.port;

import java.util.List;

public interface EmbeddingClient {
    List<Double> embed(String content);
}
