// YouTube Data API v3 채널 검색 커넥터(무료 키). 선수/체육관 채널 발굴 → research 참고.
// 키 없으면 no-op.
export async function searchChannels({ apiKey, query, max = 5, transport = globalThis.fetch }) {
  if (!apiKey) return { ok: false, reason: 'YOUTUBE_API_KEY 없음', channels: [] };
  const url = `https://www.googleapis.com/youtube/v3/search?part=snippet&type=channel&maxResults=${max}&q=${encodeURIComponent(query)}&key=${apiKey}`;
  const res = await transport(url);
  if (!res.ok) throw new Error(`YouTube ${res.status}: ${(await res.text().catch(() => '')).slice(0, 200)}`);
  const j = await res.json();
  const channels = (j.items || []).map((i) => {
    const id = i.snippet?.channelId || i.id?.channelId;
    return { channelId: id, title: i.snippet?.channelTitle || i.snippet?.title, url: `https://www.youtube.com/channel/${id}`, description: (i.snippet?.description || '').slice(0, 160) };
  });
  return { ok: true, channels };
}

export async function buildYoutubeContext({ opsRoot, agent = 'research', queries, apiKey, searchFn = searchChannels, transport, delayMs = 300, now = () => new Date() }) {
  const { writeContextSection } = await import('./context-file.mjs');
  const blocks = [];
  for (const q of queries) {
    let r;
    try { r = await searchFn({ apiKey, query: q, transport }); } catch (e) { blocks.push(`### YT: ${q}\n(실패: ${e.message})`); continue; }
    const lines = (r.channels || []).map((c) => `- [${c.title}](${c.url}) — ${c.description}`);
    blocks.push(`### YT: ${q}\n${lines.join('\n') || (r.reason || '(결과 없음)')}`);
    if (delayMs) await new Promise((rs) => setTimeout(rs, delayMs));
  }
  writeContextSection({ opsRoot, agent, section: '선수 유튜브 채널', body: blocks.join('\n\n'), now });
  return blocks.length;
}
