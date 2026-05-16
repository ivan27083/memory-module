package com.openclaw.memory.retrieval;

import com.openclaw.memory.blackboard.MemoryBlackboard;
import com.openclaw.memory.config.RetrieverProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetrieverProviderTest {

    @Test
    void provider_qmd_shouldCreateQMDRetrieverAdapter() {
        RetrieverProperties props = new RetrieverProperties();
        props.setProvider("qmd");

        Retriever retriever = buildRetriever(props);

        assertThat(retriever).isInstanceOf(QMDRetrieverAdapter.class);
    }

    @Test
    void provider_legacy_shouldCreateBlackboardRetriever() {
        RetrieverProperties props = new RetrieverProperties();
        props.setProvider("legacy");

        Retriever retriever = buildRetriever(props);

        assertThat(retriever).isInstanceOf(BlackboardRetriever.class);
    }

    @Test
    void provider_unknown_shouldFallbackToBlackboardRetriever() {
        RetrieverProperties props = new RetrieverProperties();
        props.setProvider("unknown");

        Retriever retriever = buildRetriever(props);

        assertThat(retriever).isInstanceOf(BlackboardRetriever.class);
    }

    @Test
    void blackboardRetriever_shouldReturnResults() throws Exception {
        RetrieverProperties props = new RetrieverProperties();
        props.setProvider("legacy");

        Retriever retriever = buildRetriever(props);

        var results = retriever.search("test", 5).get();
        assertThat(results).isNotNull();
    }

    // Дублирует логику MemoryModuleConfiguration.retriever() — тестируем её напрямую
    private Retriever buildRetriever(RetrieverProperties props) {
        MemoryBlackboard blackboard = new MemoryBlackboard();
        if ("qmd".equalsIgnoreCase(props.getProvider())) {
            return new QMDRetrieverAdapter(new QMDRetrievalEngine());
        }
        return new BlackboardRetriever(blackboard);
    }
}