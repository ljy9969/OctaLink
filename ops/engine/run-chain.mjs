import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { runAgent } from './run-agent.mjs';
import { createRouter } from './router.mjs';
import { loadEnv } from './env.mjs';

export async function runChain({ opsRoot, agents, router, now = () => new Date(), dailyCapUsd = 5 }) {
  const results = [];
  for (const name of agents) {
    results.push(await runAgent({ agentDir: join(opsRoot, 'agents', name), opsRoot, router, now, dailyCapUsd }));
  }
  return results;
}

// CLI: node run-chain.mjs research bug
if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const here = dirname(fileURLToPath(import.meta.url));
  const opsRoot = process.env.OPS_ROOT || join(here, '..');
  loadEnv(opsRoot); // ops/mcp/.env (모델 provider·커넥터 키)
  const agents = process.argv.slice(2);
  if (agents.length === 0) { console.error('usage: run-chain.mjs <agent...>'); process.exit(1); }
  const out = await runChain({ opsRoot, agents, router: createRouter() });
  for (const r of out) console.log(`${r.status}\t$${r.costUsd.toFixed(4)}\t${r.reportPath ?? ''}`);
}
