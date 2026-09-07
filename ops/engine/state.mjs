import { readFileSync, writeFileSync, appendFileSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

function ensure(dir) { if (!existsSync(dir)) mkdirSync(dir, { recursive: true }); }

export function loadState(stateDir, name) {
  const f = join(stateDir, `${name}.json`);
  if (!existsSync(f)) return {};
  return JSON.parse(readFileSync(f, 'utf8'));
}

export function saveState(stateDir, name, obj) {
  ensure(stateDir);
  writeFileSync(join(stateDir, `${name}.json`), JSON.stringify(obj, null, 2));
}

export function appendLedger(stateDir, entry) {
  ensure(stateDir);
  appendFileSync(join(stateDir, 'ledger.jsonl'), JSON.stringify(entry) + '\n');
}

export function spendSince(stateDir, sinceIso) {
  const f = join(stateDir, 'ledger.jsonl');
  if (!existsSync(f)) return 0;
  return readFileSync(f, 'utf8').split('\n').filter(Boolean)
    .map((l) => JSON.parse(l))
    .filter((e) => Date.parse(e.ts) >= Date.parse(sinceIso))
    .reduce((s, e) => s + (e.costUsd || 0), 0);
}
