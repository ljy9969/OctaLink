// 최소 cron 매처 (KST 기준). 필드: "min hour dom month dow"
//  min 0-59, hour 0-23, dom 1-31, month 1-12, dow 0-6(일=0). 지원: *  n  a,b  a-b  */n  a-b/n
function parseField(f, lo, hi) {
  const set = new Set();
  for (const part of f.split(',')) {
    const [range, stepStr] = part.split('/');
    const step = stepStr ? parseInt(stepStr, 10) : 1;
    let s, e;
    if (range === '*') { s = lo; e = hi; }
    else if (range.includes('-')) { const [a, b] = range.split('-').map(Number); s = a; e = b; }
    else { s = e = parseInt(range, 10); }
    for (let v = s; v <= e; v += step) set.add(v);
  }
  return set;
}
export function kstParts(date) {
  const k = new Date(date.getTime() + 9 * 3600 * 1000); // KST = UTC+9 (고정)
  return { minute: k.getUTCMinutes(), hour: k.getUTCHours(), dom: k.getUTCDate(),
    month: k.getUTCMonth() + 1, dow: k.getUTCDay() };
}
export function cronMatches(expr, date) {
  const [mi, ho, dom, mo, dow] = expr.trim().split(/\s+/);
  const p = kstParts(date);
  return parseField(mi, 0, 59).has(p.minute) && parseField(ho, 0, 23).has(p.hour)
    && parseField(dom, 1, 31).has(p.dom) && parseField(mo, 1, 12).has(p.month)
    && parseField(dow, 0, 6).has(p.dow);
}
// (fromMs, toMs] 사이 매칭 '분'이 하나라도 있으면 true. 최대 24h 캡(과도한 캐치업 방지).
export function dueSince(expr, fromMs, toMs) {
  let start = Math.max(fromMs, toMs - 24 * 3600 * 1000);
  start = Math.floor(start / 60000) * 60000 + 60000; // (from, to] → 다음 분부터
  for (let t = start; t <= toMs; t += 60000) if (cronMatches(expr, new Date(t))) return true;
  return false;
}
