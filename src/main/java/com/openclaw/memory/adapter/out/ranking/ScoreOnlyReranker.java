package com.openclaw.memory.adapter.out.ranking;

import com.openclaw.memory.domain.model.RetrievalQuery;
import com.openclaw.memory.domain.model.RetrievalResult;
import com.openclaw.memory.domain.port.Reranker;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScoreOnlyReranker implements Reranker {

    @Override
    public List<RetrievalResult> rerank(RetrievalQuery query, List<RetrievalResult> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(RetrievalResult::score).reversed()
                        .thenComparing(RetrievalResult::createdAt, Comparator.reverseOrder()))
                .toList();
    }
}
