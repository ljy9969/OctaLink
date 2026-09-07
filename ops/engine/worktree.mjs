import { execFileSync } from 'node:child_process';
import { join } from 'node:path';

export function createWorktree(repoRoot, name) {
  const rel = join('ops', '.worktrees', `${name}-${Date.now()}`);
  execFileSync('git', ['worktree', 'add', '--detach', rel], { cwd: repoRoot });
  return join(repoRoot, rel);
}

export function removeWorktree(repoRoot, wtPath) {
  execFileSync('git', ['worktree', 'remove', '--force', wtPath], { cwd: repoRoot });
}
