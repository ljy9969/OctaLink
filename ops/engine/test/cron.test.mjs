import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cronMatches, dueSince } from '../cron.mjs';

test('minute/hour match in KST', () => {
  assert.ok(cronMatches('0 9 * * *', new Date('2026-09-08T00:00:00Z')));   // KST 09:00
  assert.ok(cronMatches('30 9 * * *', new Date('2026-09-08T00:30:00Z')));  // KST 09:30
  assert.ok(!cronMatches('0 9 * * *', new Date('2026-09-08T00:30:00Z')));
});
test('lists and steps', () => {
  assert.ok(cronMatches('0 0,8,16 * * *', new Date('2026-09-07T23:00:00Z')));  // KST 08:00
  assert.ok(cronMatches('*/15 * * * *', new Date('2026-09-08T00:45:00Z')));    // minute 45
  assert.ok(!cronMatches('*/15 * * * *', new Date('2026-09-08T00:50:00Z')));
});
test('day-of-week respected (일=0)', () => {
  const d = new Date('2026-09-07T12:00:00Z'); // KST 21:00
  const dow = new Date(d.getTime() + 9 * 3600 * 1000).getUTCDay();
  assert.ok(cronMatches(`0 21 * * ${dow}`, d));
  assert.ok(!cronMatches(`0 21 * * ${(dow + 1) % 7}`, d));
});
test('dueSince fires once within window, not outside', () => {
  const to = Date.parse('2026-09-08T00:05:00Z');   // KST 09:05
  assert.ok(dueSince('0 9 * * *', Date.parse('2026-09-07T23:50:00Z'), to));  // 09:00 in window
  assert.ok(!dueSince('0 9 * * *', Date.parse('2026-09-08T00:01:00Z'), to)); // window after 09:00
});
