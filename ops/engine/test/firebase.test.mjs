import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { summarize, refreshContext } from '../../connectors/firebase.mjs';

test('summarize: 자격증명 없음(null) vs 문서', () => {
  assert.match(summarize('gyms', null), /미조회/);
  assert.match(summarize('gyms', []), /0건/);
  assert.match(summarize('gyms', [{ id: 'g1', name: '팀파시' }]), /name="팀파시"/);
});

test('refreshContext: 주입 reader로 context/<agent>.md 생성', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-fb-'));
  const readerFn = async ({ collection }) => collection === 'gyms'
    ? [{ id: 'g1', name: 'Octagon MMA' }, { id: 'g2', name: '팀파시' }] : [];
  const written = await refreshContext({
    opsRoot: ops, projectId: 'octalink-28088', credentialJson: '{}',
    contextMap: { research: ['gyms', 'publicProfiles'] }, readerFn, now: () => new Date('2026-09-08T00:00:00Z') });
  assert.deepEqual(written, ['research']);
  const md = readFileSync(join(ops, 'context', 'research.md'), 'utf8');
  assert.match(md, /Octagon MMA/);
  assert.match(md, /publicProfiles: \(0건\)/);
});
