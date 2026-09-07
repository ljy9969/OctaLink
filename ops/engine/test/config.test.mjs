import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { loadAgentConfig } from '../config.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const dummy = join(here, '..', 'fixtures', 'dummy');

test('loads config + skill + verifier content', () => {
  const c = loadAgentConfig(dummy);
  assert.equal(c.name, 'dummy');
  assert.equal(c.risk, 'safe');
  assert.equal(c.model.provider, 'dryrun');
  assert.equal(c.budget.maxRetries, 2);
  assert.match(c.skill, /DUMMY SKILL/);
  assert.match(c.verifier, /pass/);
});

test('throws on missing required field', () => {
  assert.throws(() => loadAgentConfig(join(here, 'nope')), /config/);
});
