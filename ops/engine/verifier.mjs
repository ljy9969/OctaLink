function extractJson(text) {
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start === -1 || end === -1 || end < start) return null;
  try { return JSON.parse(text.slice(start, end + 1)); } catch { return null; }
}

export async function runVerifier({ router, model, verifierPrompt, artifact }) {
  const { text } = await router.complete({
    provider: model.startsWith('anthropic') ? 'anthropic'
      : model.startsWith('ollama') ? 'ollama'
      : model === 'dryrun' ? 'dryrun' : 'openrouter',
    model,
    system: verifierPrompt,
    messages: [{ role: 'user', content: `ARTIFACT:\n${artifact}\n\nRespond with ONLY the JSON verdict.` }],
  });
  const parsed = extractJson(text);
  if (!parsed || typeof parsed.pass !== 'boolean') {
    return { pass: false, checks: [{ name: 'parse', pass: false, reason: 'verifier did not return valid verdict JSON' }] };
  }
  return { pass: parsed.pass, checks: Array.isArray(parsed.checks) ? parsed.checks : [] };
}
