// Price table: USD per 1,000,000 tokens [input, output].
const PRICES = {
  // Claude (Anthropic API 키 경로). Max 구독(anthropic-oauth) 경로는 종량 과금이 없어 cost 0.
  'anthropic/claude-opus-5': [5, 25],
  'anthropic/claude-sonnet-5': [2, 10],
  'anthropic/claude-haiku-4-5': [1, 5],
  // OpenRouter 오픈소스
  'openrouter/deepseek/deepseek-chat': [0.27, 1.10],
  'openrouter/meta-llama/llama-3.3-70b-instruct': [0.13, 0.40],
  'openrouter/qwen/qwen-2.5-72b-instruct': [0.35, 0.40],
};

export function estimateCost(model, usage) {
  const [pin, pout] = PRICES[model] ?? [0, 0];
  return (usage.inputTokens / 1e6) * pin + (usage.outputTokens / 1e6) * pout;
}

export function createRouter({ transport = globalThis.fetch, env = process.env } = {}) {
  function dryrun(model, messages) {
    const last = messages[messages.length - 1]?.content ?? '';
    return { text: `DRYRUN[${model}] ${String(last).slice(0, 120)}`,
      usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 } };
  }
  async function complete({ provider, model, system, messages, maxTokens = 1024 }) {
    if (provider === 'dryrun') return dryrun(model, messages);

    if (provider === 'openrouter') {
      const key = env.OPENROUTER_API_KEY;
      if (!key) return dryrun(model, messages);
      const res = await transport('https://openrouter.ai/api/v1/chat/completions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` },
        body: JSON.stringify({ model: model.replace(/^openrouter\//, ''),
          max_tokens: maxTokens,
          messages: [{ role: 'system', content: system }, ...messages] }),
      });
      const j = await res.json();
      const usage = { inputTokens: j.usage?.prompt_tokens ?? 0, outputTokens: j.usage?.completion_tokens ?? 0 };
      return { text: j.choices?.[0]?.message?.content ?? '', usage: { ...usage, costUsd: estimateCost(model, usage) } };
    }

    if (provider === 'anthropic') {
      const key = env.ANTHROPIC_API_KEY;
      if (!key) return dryrun(model, messages);
      const res = await transport('https://api.anthropic.com/v1/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-api-key': key, 'anthropic-version': '2023-06-01' },
        body: JSON.stringify({ model: model.replace(/^anthropic\//, ''),
          max_tokens: maxTokens, system, messages }),
      });
      const j = await res.json();
      const usage = { inputTokens: j.usage?.input_tokens ?? 0, outputTokens: j.usage?.output_tokens ?? 0 };
      const text = (j.content ?? []).map((b) => b.text ?? '').join('');
      return { text, usage: { ...usage, costUsd: estimateCost(model, usage) } };
    }

    if (provider === 'ollama') {
      const res = await transport(`${env.OLLAMA_URL ?? 'http://localhost:11434'}/api/chat`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ model: model.replace(/^ollama\//, ''), stream: false,
          messages: [{ role: 'system', content: system }, ...messages] }),
      });
      const j = await res.json();
      return { text: j.message?.content ?? '', usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 } };
    }

    throw new Error(`unknown provider: ${provider}`);
  }
  return { complete, _transport: transport, _env: env };
}
