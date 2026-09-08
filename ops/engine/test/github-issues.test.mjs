import { test } from 'node:test';
import assert from 'node:assert/strict';
import { checkAssignedIssue } from '../../connectors/github-issues.mjs';

function res(body, ok = true, status = 200) {
  return { ok, status, async json() { return body; }, async text() { return JSON.stringify(body); } };
}

test('no token → no-op', async () => {
  const r = await checkAssignedIssue({ repo: 'x/y', token: '', sinceMs: 0, transport: async () => { throw new Error('nope'); } });
  assert.equal(r.fired, false);
});

test('returns newest assigned open issue (PR·미배정 제외), sends auth + since', async () => {
  let captured;
  const transport = async (url, init) => {
    captured = { url, init };
    return res([
      { number: 7, title: '교류전 버그', assignee: { login: 'ljy9969' }, html_url: 'u7', body: '재현: ...' },
      { number: 6, title: 'PR', assignee: { login: 'x' }, pull_request: {}, html_url: 'u6' },
      { number: 5, title: '미배정', assignee: null, html_url: 'u5' },
    ]);
  };
  const r = await checkAssignedIssue({ repo: 'ljy9969/OctaLink', token: 'tok', sinceMs: Date.parse('2026-09-08T00:00:00Z'), transport });
  assert.match(captured.url, /repos\/ljy9969\/OctaLink\/issues/);
  assert.match(captured.url, /since=2026-09-08/);
  assert.equal(captured.init.headers.Authorization, 'Bearer tok');
  assert.equal(r.fired, true);
  assert.equal(r.issue.number, 7);
  assert.equal(r.issue.assignee, 'ljy9969');
});

test('배정 이슈 없으면 fired:false', async () => {
  const r = await checkAssignedIssue({ repo: 'x/y', token: 't', sinceMs: 0, transport: async () => res([{ number: 1, assignee: null }]) });
  assert.equal(r.fired, false);
});

test('non-2xx → throw', async () => {
  await assert.rejects(
    checkAssignedIssue({ repo: 'x/y', token: 't', sinceMs: 0, transport: async () => res({ message: 'bad' }, false, 401) }),
    /GitHub 401/);
});
