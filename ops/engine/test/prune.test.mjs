import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, existsSync, utimesSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pruneApprovals, stampTime } from '../prune.mjs';

const NOW = new Date('2026-10-15T03:00:00Z'); // KST 10-15 12:00

function makeDir() {
  const dir = mkdtempSync(join(tmpdir(), 'ops-prune-'));
  const w = (n) => writeFileSync(join(dir, n), 'x');
  w('.gitkeep');
  w('social-20261001-230339.md');  // 13.2일 전 → 삭제
  w('ads-20261008-030001.md');     // 6.99일 전(경계 직후) → 보존
  w('support-20261007-235959.md'); // 7일 3초 전 → 삭제
  w('dev-20261014-040302.md');     // 1일 전 → 보존
  w('notes.txt');                  // .md 아님 → 무시
  // 스탬프 없는 파일: mtime 으로 판단
  w('inquiry-old.md'); const old = new Date('2026-09-18T05:00:00Z');
  utimesSync(join(dir, 'inquiry-old.md'), old, old);
  w('inquiry-new.md'); const fresh = new Date('2026-10-14T00:00:00Z');
  utimesSync(join(dir, 'inquiry-new.md'), fresh, fresh);
  return dir;
}

test('stampTime: gate.mjs 파일명 스탬프를 UTC로 파싱, 없으면 null', () => {
  assert.equal(stampTime('social-20261001-230339.md'), Date.UTC(2026, 9, 1, 23, 3, 39));
  assert.equal(stampTime('inquiry-j7HHQWKDcnXr20LwZ7YJ.md'), null);
});

test('pruneApprovals: 7일 지난 .md만 삭제, .gitkeep·비 md 보존, 스탬프 없으면 mtime', () => {
  const dir = makeDir();
  const r = pruneApprovals({ dir, now: NOW, keepDays: 7 });
  assert.deepEqual(r.removed, ['inquiry-old.md', 'social-20261001-230339.md', 'support-20261007-235959.md']);
  assert.equal(r.kept, 3); // ads-1008, dev-1014, inquiry-new
  for (const n of r.removed) assert.equal(existsSync(join(dir, n)), false);
  for (const n of ['.gitkeep', 'notes.txt', 'ads-20261008-030001.md', 'dev-20261014-040302.md', 'inquiry-new.md']) {
    assert.equal(existsSync(join(dir, n)), true, n);
  }
});

test('pruneApprovals: dryRun 은 목록만, 파일은 그대로', () => {
  const dir = makeDir();
  const r = pruneApprovals({ dir, now: NOW, keepDays: 7, dryRun: true });
  assert.equal(r.removed.length, 3);
  assert.equal(existsSync(join(dir, 'social-20261001-230339.md')), true);
});

test('pruneApprovals: 폴더 없으면 no-op', () => {
  assert.deepEqual(pruneApprovals({ dir: join(tmpdir(), 'no-such-approvals-dir'), now: NOW }), { removed: [], kept: 0 });
});
