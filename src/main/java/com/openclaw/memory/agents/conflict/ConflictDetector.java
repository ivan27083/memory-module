package com.openclaw.memory.agents.conflict;

import com.openclaw.memory.domain.model.MemoryRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Detects conflicts among a list of MemoryRecords by grouping them on
 * (agentId + subject) and comparing content within each group.
 *
 * Rules:
 *  1. Two records conflict when they share agentId + subject but differ in content.
 *  2. Winner = highest metadata["confidence"]; tiebreak = newer validFrom.
 *  3. Pure function — no I/O, no side effects. Callers own event emission and mutation.
 */
@Component
public class ConflictDetector {

    public record ConflictReport(
            UUID winnerId,
            UUID loserId,
            String agentId,
            String subject,
            ConflictType type,
            double severity,
            String resolutionReason
    ) {}

    public enum ConflictType {
        SAME_SUBJECT_DIFFERENT_CONTENT,
        CONFIDENCE_INVERSION
    }

    /**
     * Detect conflicts in the given records and return one report per (winner, loser) pair.
     * Records without a "subject" in metadata are ignored.
     */
    public List<ConflictReport> detect(List<MemoryRecord> records) {
        // Only active records can participate in a conflict
        Map<String, List<MemoryRecord>> grouped = records.stream()
                .filter(MemoryRecord::isActive)
                .filter(r -> r.metadata().containsKey("subject"))
                .collect(Collectors.groupingBy(r ->
                        r.agentId() + "" + r.metadata().get("subject")));

        List<ConflictReport> reports = new ArrayList<>();

        for (List<MemoryRecord> group : grouped.values()) {
            if (group.size() < 2) continue;

            // Rank: highest confidence first, then newest validFrom
            List<MemoryRecord> ranked = group.stream()
                    .sorted(Comparator
                            .<MemoryRecord>comparingDouble(r -> confidence(r))
                            .reversed()
                            .thenComparing(r -> r.validFrom(), Comparator.reverseOrder()))
                    .toList();

            MemoryRecord winner = ranked.get(0);
            String subject  = String.valueOf(winner.metadata().get("subject"));

            for (int i = 1; i < ranked.size(); i++) {
                MemoryRecord loser = ranked.get(i);

                // Content-identical records are not conflicts (duplicate writes)
                if (winner.content().equals(loser.content())) continue;

                double winnerConf = confidence(winner);
                double loserConf  = confidence(loser);
                double severity   = Math.max(0.0, Math.min(1.0, 1.0 - (winnerConf - loserConf)));

                ConflictType type = (winnerConf > 0.8 && loserConf > 0.8)
                        ? ConflictType.CONFIDENCE_INVERSION
                        : ConflictType.SAME_SUBJECT_DIFFERENT_CONTENT;

                reports.add(new ConflictReport(
                        winner.id(),
                        loser.id(),
                        winner.agentId(),
                        subject,
                        type,
                        severity,
                        String.format("confidence arbitration: winner=%.2f loser=%.2f",
                                winnerConf, loserConf)
                ));
            }
        }

        return reports;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static double confidence(MemoryRecord record) {
        Object v = record.metadata().get("confidence");
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); }
        catch (Exception e) { return 0.5; }
    }
}
