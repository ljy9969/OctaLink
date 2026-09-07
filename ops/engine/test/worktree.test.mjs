import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, existsSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createWorktree, removeWorktree } from '../worktree.mjs';

test('creates and removes a worktree in a temp git repo', () => {
  const repo = mkdtempSync(join(tmpdir(), 'ops-wt-'));
  const git = (...a) => execFileSync('git', a, { cwd: repo });
  git('init', '-q');
  git('config', 'user.email', 't@t');
  git('config', 'user.name', 't');
  writeFileSync(join(repo, 'f.txt'), 'x');
  git('add', '.'); git('commit', '-qm', 'init');
  const wt = createWorktree(repo, 'dummy');
  assert.ok(existsSync(wt));
  removeWorktree(repo, wt);
  assert.ok(!existsSync(wt));
});
