import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { loadState, saveState, appendLedger, spendSince } from '../state.mjs';

test('save/load round-trips and defaults to {}', () => {
  const dir = mkdtempSync(join(tmpdir(), 'ops-state-'));
  assert.deepEqual(loadState(dir, 'a'), {});
  saveState(dir, 'a', { runs: 3 });
  assert.deepEqual(loadState(dir, 'a'), { runs: 3 });
});

test('ledger sums spend since a timestamp', () => {
  const dir = mkdtempSync(join(tmpdir(), 'ops-led-'));
  appendLedger(dir, { ts: '2026-09-07T00:00:00Z', agent: 'a', costUsd: 0.10 });
  appendLedger(dir, { ts: '2026-09-07T10:00:00Z', agent: 'b', costUsd: 0.25 });
  assert.ok(Math.abs(spendSince(dir, '2026-09-07T05:00:00Z') - 0.25) < 1e-9);
  assert.ok(Math.abs(spendSince(dir, '2026-09-07T00:00:00Z') - 0.35) < 1e-9);
});
