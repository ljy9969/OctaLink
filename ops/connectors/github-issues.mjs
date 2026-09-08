// GitHub 이슈 이벤트 소스(제로 의존성, fetch). 지정 레포에서 sinceMs 이후 갱신된
// '배정된 열린 이슈'를 찾아 dev 트리거 후보를 반환한다. 토큰 없으면 no-op.
export async function checkAssignedIssue({ repo, token, sinceMs, transport = globalThis.fetch }) {
  if (!token) return { fired: false, reason: 'no token' };
  const since = new Date(sinceMs).toISOString();
  const url = `https://api.github.com/repos/${repo}/issues?state=open&assignee=*&since=${encodeURIComponent(since)}&sort=updated&direction=desc&per_page=20`;
  const res = await transport(url, { headers: {
    Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'octalink-ops' } });
  if (!res.ok) throw new Error(`GitHub ${res.status}: ${(await res.text().catch(() => '')).slice(0, 200)}`);
  const issues = await res.json();
  const assigned = (Array.isArray(issues) ? issues : []).filter((i) => i.assignee && !i.pull_request);
  if (assigned.length === 0) return { fired: false };
  const i = assigned[0]; // 가장 최근 갱신된 배정 이슈
  return { fired: true, issue: { number: i.number, title: i.title, assignee: i.assignee.login, url: i.html_url, body: (i.body || '').slice(0, 800) } };
}
