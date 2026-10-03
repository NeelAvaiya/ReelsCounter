import { formatRelativeTime } from '../src/utils/time';

const now = 1_727_940_000_000;

test.each([
  [0, 'None yet'],
  [now - 10_000, 'Just now'],
  [now - 5 * 60_000, '5 min ago'],
  [now - 3 * 3_600_000, '3 h ago'],
])('formatRelativeTime(%p)', (ts, expected) => {
  expect(formatRelativeTime(ts, now)).toBe(expected);
});
