import express from 'express';
import { createStore } from '@tobilu/qmd';
import { writeFile, unlink, mkdir, readFile } from 'fs/promises';
import { join, basename } from 'path';

const MEMORIES_DIR = process.env.QMD_MEMORIES_DIR || './qmd-memories';
const DB_PATH      = process.env.QMD_DB_PATH      || './qmd.sqlite';
const PORT         = parseInt(process.env.QMD_PORT || '9090', 10);

const app = express();
app.use(express.json({ limit: '10mb' }));

let store;

// ── Init ──────────────────────────────────────────────────────────────────────

async function init() {
  await mkdir(MEMORIES_DIR, { recursive: true });

  store = await createStore({
    dbPath: DB_PATH,
    config: {
      collections: {
        memories: { path: MEMORIES_DIR, pattern: '**/*.md' },
      },
    },
  });

  // Index whatever is already on disk (restart-safe)
  await store.update({ collections: ['memories'] });
  console.log(`[qmd-server] store ready. memories=${MEMORIES_DIR} db=${DB_PATH}`);
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function buildMarkdown({ id, agentId, sessionId, type, content, metadata = {}, createdAt }) {
  const fm = [
    '---',
    `id: ${id}`,
    `agentId: ${agentId ?? ''}`,
    `sessionId: ${sessionId ?? ''}`,
    `type: ${type ?? 'EPISODIC'}`,
    `createdAt: ${createdAt ?? new Date().toISOString()}`,
    ...Object.entries(metadata).map(([k, v]) => `${k}: ${String(v)}`),
    '---',
    '',
    content,
  ];
  return fm.join('\n');
}

// Extract frontmatter value by key (simple line-based parse, no deps needed)
function fmValue(text, key) {
  const line = text.split('\n').find(l => l.startsWith(`${key}:`));
  return line ? line.slice(key.length + 1).trim() : undefined;
}

// Map a raw QMD search hit to our canonical response shape.
// QMD result objects expose: path, content/snippet, score, metadata
function mapResult(hit) {
  const rawId = hit.id
    ?? (hit.path ? basename(hit.path, '.md') : undefined)
    ?? crypto.randomUUID();

  return {
    id:       rawId,
    content:  hit.content ?? hit.snippet ?? hit.text ?? '',
    score:    hit.score   ?? hit.relevance ?? 0.5,
    metadata: hit.metadata ?? {},
  };
}

// ── Routes ────────────────────────────────────────────────────────────────────

// POST /index  — write a memory as markdown and reindex
app.post('/index', async (req, res) => {
  try {
    const { id } = req.body;
    if (!id) return res.status(400).json({ error: 'id is required' });

    const md   = buildMarkdown(req.body);
    const file = join(MEMORIES_DIR, `${id}.md`);

    await writeFile(file, md, 'utf-8');
    await store.update({ collections: ['memories'] });

    res.json({ success: true, id });
  } catch (err) {
    console.error('[qmd-server] /index error:', err.message);
    res.status(500).json({ error: err.message });
  }
});

// DELETE /index/:id  — remove a memory and reindex
app.delete('/index/:id', async (req, res) => {
  try {
    await unlink(join(MEMORIES_DIR, `${req.params.id}.md`));
    await store.update({ collections: ['memories'] });
    res.json({ success: true });
  } catch {
    res.status(404).json({ error: 'not found' });
  }
});

// POST /search  — hybrid search (BM25 + vector + optional rerank)
app.post('/search', async (req, res) => {
  try {
    const { query, limit = 10, rerank = false, minScore = 0.0 } = req.body;
    if (!query) return res.status(400).json({ error: 'query is required' });

    const hits = await store.search({
      query,
      collection: 'memories',
      limit: Math.min(limit * 3, 60),   // over-fetch; we filter + slice below
      rerank,
      minScore,
    });

    const results = hits
      .map(mapResult)
      .filter(r => r.score >= minScore)
      .slice(0, limit);

    res.json({ results });
  } catch (err) {
    console.error('[qmd-server] /search error:', err.message);
    res.status(500).json({ error: err.message });
  }
});

// POST /embed  — trigger embedding pass (async, returns immediately)
app.post('/embed', async (req, res) => {
  try {
    store.embed({ force: req.body?.force ?? false }).catch(e =>
      console.warn('[qmd-server] embed background error:', e.message)
    );
    res.json({ status: 'started' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /health
app.get('/health', (_req, res) =>
  res.json({ status: 'ok', memoriesDir: MEMORIES_DIR, dbPath: DB_PATH })
);

// ── Start ─────────────────────────────────────────────────────────────────────

init()
  .then(() => app.listen(PORT, () => console.log(`[qmd-server] listening on :${PORT}`)))
  .catch(err => { console.error('[qmd-server] fatal init error:', err); process.exit(1); });
