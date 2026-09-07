// 에이전트 모델 프로필 적용기. 사용: node ops/engine/apply-profile.mjs <openrouter|max>
// 프로필(ops/profiles/<name>.json)의 provider/name(+trigger)을 각 에이전트 config.json에 기록.
import { readFileSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export function applyProfile(opsRoot, profileName) {
  const profile = JSON.parse(readFileSync(join(opsRoot, 'profiles', `${profileName}.json`), 'utf8'));
  const changed = [];
  for (const [agent, m] of Object.entries(profile.agents)) {
    const cfgPath = join(opsRoot, 'agents', agent, 'config.json');
    const cfg = JSON.parse(readFileSync(cfgPath, 'utf8'));
    cfg.model = { provider: m.provider, name: m.name };
    if (m.trigger) cfg.trigger = m.trigger;
    writeFileSync(cfgPath, JSON.stringify(cfg, null, 2) + '\n');
    changed.push(`${agent.padEnd(9)} ${m.provider}:${m.name.split('/').pop()}${m.trigger ? '  @ ' + m.trigger : ''}`);
  }
  return changed;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const here = dirname(fileURLToPath(import.meta.url));
  const opsRoot = process.env.OPS_ROOT || join(here, '..');
  const name = process.argv[2];
  if (!['openrouter', 'ollama'].includes(name)) { console.error('usage: apply-profile.mjs <openrouter|ollama>'); process.exit(1); }
  const changed = applyProfile(opsRoot, name);
  console.log(`applied profile "${name}":`);
  for (const c of changed) console.log('  ' + c);
}
