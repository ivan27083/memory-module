-- PR-5: Temporal layer
-- Adds validFrom / validTo / supersededBy to episodic_memory so that
-- supersession replaces destructive overwrite and the system can answer
-- "what was true at time T?" without losing history.

ALTER TABLE episodic_memory
    ADD COLUMN IF NOT EXISTS valid_from    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS valid_to      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS superseded_by UUID;

-- Fast lookup of currently active records per agent
CREATE INDEX IF NOT EXISTS idx_episodic_memory_active
    ON episodic_memory (agent_id, valid_from DESC)
    WHERE superseded_by IS NULL AND valid_to IS NULL;

-- Allows filtering by subject for supersession lookup
CREATE INDEX IF NOT EXISTS idx_episodic_memory_subject
    ON episodic_memory ((metadata->>'subject'))
    WHERE metadata->>'subject' IS NOT NULL;
