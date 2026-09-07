import { readFileSync } from 'node:fs';
import { join } from 'node:path';

export function loadAgentConfig(agentDir) {
  let raw;
  try {
    raw = JSON.parse(readFileSync(join(agentDir, 'config.json'), 'utf8'));
  } catch (e) {
    throw new Error(`cannot read config at ${agentDir}: ${e.message}`);
  }
  for (const f of ['name', 'risk', 'trigger', 'model', 'budget', 'stop']) {
    if (raw[f] === undefined) throw new Error(`config missing field: ${f}`);
  }
  if (!raw.model.provider || !raw.model.name) throw new Error('config missing model.provider/name');
  if (raw.budget.maxRetries === undefined || raw.budget.maxUsd === undefined) {
    throw new Error('config missing budget.maxRetries/maxUsd');
  }
  return {
    ...raw,
    isolate: Boolean(raw.isolate),
    skill: readFileSync(join(agentDir, 'skill.md'), 'utf8'),
    verifier: readFileSync(join(agentDir, 'verifier.md'), 'utf8'),
  };
}
