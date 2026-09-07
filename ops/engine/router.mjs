// Price table: USD per 1,000,000 tokens [input, output].
const PRICES = {
  'anthropic/claude-opus-4': [15, 75],
  'anthropic/claude-sonnet-4': [3, 15],
  'openrouter/deepseek/deepseek-chat': [0.27, 1.10],
  'openrouter/meta-llama/llama-3.3-70b-instruct': [0.13, 0.40],
};

export function estimateCost(model, usage) {
  const [pin, pout] = PRICES[model] ?? [0, 0];
  return (usage.inputTokens / 1e6) * pin + (usage.outputTokens / 1e6) * pout;
}

export function createRouter({ transport = globalThis.fetch, env = process.env } = {}) {
  async function complete({ provider, model, system, messages, maxTokens = 1024 }) {
    if (provider === 'dryrun') {
      const last = messages[messages.length - 1]?.content ?? '';
      return {
        text: `DRYRUN[${model}] ${String(last).slice(0, 120)}`,
        usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 },
      };
    }
    throw new Error(`provider not implemented in Task 1: ${provider}`);
  }
  return { complete, _transport: transport, _env: env };
}
