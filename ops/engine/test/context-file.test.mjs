import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { writeContextSection } from '../../connectors/context-file.mjs';

test('구획 병합: 서로 다른 구획이 공존하고, 재작성 시 자기 구획만 교체', () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-ctx-'));
  const now = () => new Date('2026-09-11T00:00:00Z');
  writeContextSection({ opsRoot: ops, agent: 'research', section: '웹 검색', body: 'WEB-A', now });
  writeContextSection({ opsRoot: ops, agent: 'research', section: '앱 데이터', body: 'APP-A', now });
  let md = readFileSync(join(ops, 'context', 'research.md'), 'utf8');
  assert.match(md, /WEB-A/);
  assert.match(md, /APP-A/); // 두 구획 공존

  // '웹 검색'만 갱신 → 앱 데이터 구획은 보존
  writeContextSection({ opsRoot: ops, agent: 'research', section: '웹 검색', body: 'WEB-B', now });
  md = readFileSync(join(ops, 'context', 'research.md'), 'utf8');
  assert.match(md, /WEB-B/);
  assert.doesNotMatch(md, /WEB-A/); // 옛 웹 구획은 교체됨
  assert.match(md, /APP-A/);        // 앱 데이터는 그대로
  assert.equal((md.match(/sec:웹 검색/g) || []).length, 2); // start+end 마커 1쌍(중복 아님)
});
