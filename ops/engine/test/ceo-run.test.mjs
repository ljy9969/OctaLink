import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, existsSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { runCeo } from '../ceo-run.mjs';

test('CEO reads context, writes tasks/<agent>.md and state/ceo.json', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-ceo-'));
  for (const a of ['research', 'social']) mkdirSync(join(ops, 'agents', a), { recursive: true });
  mkdirSync(join(ops, 'ceo'), { recursive: true });
  writeFileSync(join(ops, 'ceo', 'config.json'), JSON.stringify({ model: { provider: 'dryrun', name: 'dryrun' } }));
  writeFileSync(join(ops, 'ceo', 'ceo.md'), 'CEO');
  const router = { complete: async () => ({
    text: '{"priorities":["테스터 +6"],"tasks":{"research":"경쟁앱 3개 조사","social":"릴스 2건","ghost":"무시"}}',
    usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 } }) };
  const r = await runCeo({ opsRoot: ops, router, now: () => new Date('2026-09-08T00:00:00Z') });
  assert.deepEqual(r.tasksWritten.sort(), ['research', 'social']);   // ghost(존재X) 제외
  assert.ok(existsSync(join(ops, 'tasks', 'research.md')));
  assert.match(readFileSync(join(ops, 'tasks', 'social.md'), 'utf8'), /릴스 2건/);
  assert.equal(JSON.parse(readFileSync(join(ops, 'state', 'ceo.json'), 'utf8')).priorities[0], '테스터 +6');
});
