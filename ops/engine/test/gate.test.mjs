import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, existsSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { applyGate } from '../gate.mjs';

test('safe risk auto-applies without file', () => {
  const dir = mkdtempSync(join(tmpdir(), 'ops-gate-'));
  const r = applyGate({ risk: 'safe', name: 'research', artifact: 'x', approvalsDir: dir, now: new Date('2026-09-07T01:02:03Z') });
  assert.deepEqual(r, { action: 'auto' });
});

test('gated risk writes a proposal file', () => {
  const dir = mkdtempSync(join(tmpdir(), 'ops-gate2-'));
  const r = applyGate({ risk: 'gated', name: 'social', artifact: 'POST BODY', approvalsDir: dir, now: new Date('2026-09-07T01:02:03Z') });
  assert.equal(r.action, 'queued');
  assert.ok(existsSync(r.path));
  assert.match(readFileSync(r.path, 'utf8'), /POST BODY/);
});
