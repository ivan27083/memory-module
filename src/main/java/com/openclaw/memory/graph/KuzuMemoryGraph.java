package com.openclaw.memory.graph;

import com.kuzudb.Connection;
import com.kuzudb.Database;
import com.kuzudb.QueryResult;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.port.MemoryGraphPort;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kuzu-backed graph store for memory supersession and causal chains.
 *
 * Schema:
 *   NODE Memory(id STRING PK, agentId STRING, validFromMs INT64)
 *   REL  SUPERSEDES(FROM Memory TO Memory, at INT64)
 *   REL  CAUSED_BY (FROM Memory TO Memory)
 *
 * Activated when memory.graph.backend=kuzu.
 * The database is opened from the path configured via memory.graph.kuzu.path.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "memory.graph.backend", havingValue = "kuzu")
public class KuzuMemoryGraph implements MemoryGraphPort {

    private final Database database;
    private final Connection connection;

    public KuzuMemoryGraph(
            @Value("${memory.graph.kuzu.path:./data/kuzu_graph}") String dbPath)
            throws IOException {
        // Create only the parent directory — Kuzu creates the database directory itself.
        // Pre-creating the target directory causes "Database path cannot be a directory".
        Path parent = Path.of(dbPath).getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        log.info("Opening Kuzu graph database at {}", dbPath);
        this.database   = new Database(dbPath);
        this.connection = new Connection(database);
        initSchema();
    }

    // ── MemoryGraphPort ───────────────────────────────────────────────────────

    @Override
    public synchronized void addMemory(MemoryRecord record) {
        String id       = record.id().toString();
        String agentId  = escape(record.agentId());
        long   validMs  = record.validFrom().toEpochMilli();
        try {
            // Skip if already indexed (idempotent)
            QueryResult exists = connection.query(
                    "MATCH (m:Memory {id: '" + id + "'}) RETURN count(m)");
            if (exists.hasNext() && !"0".equals(exists.getNext().getValue(0).toString())) {
                exists.close();
                return;
            }
            exists.close();

            connection.query(String.format(
                    "CREATE (:Memory {id: '%s', agentId: '%s', validFromMs: %d})",
                    id, agentId, validMs));
            log.debug("Graph: indexed memory {}", id);
        } catch (Exception e) {
            log.warn("Graph addMemory failed for {}: {}", id, e.getMessage());
        }
    }

    @Override
    public synchronized void recordSupersession(UUID oldId, UUID newId, Instant at) {
        try {
            connection.query(String.format(
                    "MATCH (old:Memory {id: '%s'}), (new:Memory {id: '%s'}) " +
                    "CREATE (old)-[:SUPERSEDES {at: %d}]->(new)",
                    oldId, newId, at.toEpochMilli()));
            log.debug("Graph: {} supersedes {}", newId, oldId);
        } catch (Exception e) {
            log.warn("Graph recordSupersession failed {} -> {}: {}", oldId, newId, e.getMessage());
        }
    }

    @Override
    public synchronized List<String> getSupersessionChain(String memoryId) {
        List<String> chain = new ArrayList<>();
        try {
            QueryResult r = connection.query(String.format(
                    "MATCH (m:Memory {id: '%s'})-[:SUPERSEDES*1..]->(s:Memory) " +
                    "RETURN s.id ORDER BY s.validFromMs ASC",
                    memoryId));
            while (r.hasNext()) {
                chain.add(r.getNext().getValue(0).toString());
            }
            r.close();
        } catch (Exception e) {
            log.warn("Graph getSupersessionChain failed for {}: {}", memoryId, e.getMessage());
        }
        return chain;
    }

    @Override
    public synchronized boolean isConsistent(String artifactId, LocalDateTime atTime) {
        // Edge direction: old -[SUPERSEDES]-> new.
        // An artifact has been superseded when it has an outgoing SUPERSEDES edge.
        try {
            QueryResult r = connection.query(String.format(
                    "MATCH (m:Memory {id: '%s'})-[:SUPERSEDES]->() RETURN count(*)",
                    artifactId));
            if (r.hasNext()) {
                String count = r.getNext().getValue(0).toString();
                r.close();
                return "0".equals(count);
            }
            r.close();
        } catch (Exception e) {
            log.warn("Graph isConsistent check failed for {}: {}", artifactId, e.getMessage());
        }
        return true;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PreDestroy
    public void close() {
        try { connection.close(); } catch (Exception ignored) {}
        try { database.close();   } catch (Exception ignored) {}
        log.info("Kuzu graph database closed");
    }

    // ── Schema init ───────────────────────────────────────────────────────────

    private void initSchema() {
        ddl("CREATE NODE TABLE IF NOT EXISTS Memory(" +
                "id STRING, agentId STRING, validFromMs INT64, PRIMARY KEY(id))");
        ddl("CREATE REL TABLE IF NOT EXISTS SUPERSEDES(" +
                "FROM Memory TO Memory, at INT64)");
        ddl("CREATE REL TABLE IF NOT EXISTS CAUSED_BY(FROM Memory TO Memory)");
        log.info("Kuzu schema ready");
    }

    private void ddl(String query) {
        try {
            connection.query(query);
        } catch (Exception e) {
            // Table already exists in databases opened from an existing path
            if (!e.getMessage().contains("already exists")) {
                log.error("Kuzu DDL error [{}]: {}", query, e.getMessage());
            }
        }
    }

    private static String escape(String s) {
        return s != null ? s.replace("\\", "\\\\").replace("'", "\\'") : "";
    }
}
