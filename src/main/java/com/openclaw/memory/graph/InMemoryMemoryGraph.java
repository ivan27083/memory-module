package com.openclaw.memory.graph;

import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.port.MemoryGraphPort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMemoryGraph implements MemoryGraphPort {

    private final Map<UUID, UUID> supersededBy = new ConcurrentHashMap<>();
    private final Map<UUID, List<UUID>> successors = new ConcurrentHashMap<>();

    @Override
    public void addMemory(MemoryRecord record) {
        successors.putIfAbsent(record.id(), new ArrayList<>());
    }

    @Override
    public void recordSupersession(UUID oldId, UUID newId, Instant at) {
        supersededBy.put(oldId, newId);
        successors.computeIfAbsent(oldId, k -> new ArrayList<>()).add(newId);
    }

    @Override
    public List<String> getSupersessionChain(String memoryId) {
        UUID id = UUID.fromString(memoryId);
        List<String> chain = new ArrayList<>();
        collectSuccessors(id, chain);
        return chain;
    }

    @Override
    public boolean isConsistent(String artifactId, LocalDateTime atTime) {
        return !supersededBy.containsKey(UUID.fromString(artifactId));
    }

    private void collectSuccessors(UUID id, List<String> result) {
        for (UUID next : successors.getOrDefault(id, List.of())) {
            result.add(next.toString());
            collectSuccessors(next, result);
        }
    }
}
