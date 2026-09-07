import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { applyProfile } from '../apply-profile.mjs';

test('applyProfile writes model + trigger into agent config.json', () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-prof-'));
  mkdirSync(join(ops, 'agents', 'x'), { recursive: true });
  mkdirSync(join(ops, 'profiles'), { recursive: true });
  writeFileSync(join(ops, 'agents', 'x', 'config.json'), JSON.stringify({
    name: 'x', risk: 'safe', trigger: 'old', model: { provider: 'dryrun', name: 'dryrun' },
    budget: { maxRetries: 1, maxUsd: 0.1 }, stop: 'verifier_pass', isolate: false }));
  writeFileSync(join(ops, 'profiles', 'p.json'), JSON.stringify({
    agents: { x: { provider: 'anthropic-oauth', name: 'anthropic/claude-sonnet-5', trigger: 'cron:0 9 * * *' } } }));
  const changed = applyProfile(ops, 'p');
  const cfg = JSON.parse(readFileSync(join(ops, 'agents', 'x', 'config.json'), 'utf8'));
  assert.equal(cfg.model.provider, 'anthropic-oauth');
  assert.equal(cfg.model.name, 'anthropic/claude-sonnet-5');
  assert.equal(cfg.trigger, 'cron:0 9 * * *');
  assert.equal(cfg.risk, 'safe');  // 나머지 필드 보존
  assert.equal(changed.length, 1);
});
