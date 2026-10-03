/** "just now", "5 min ago", "3 h ago", or a date for older timestamps. */
export function formatRelativeTime(timestamp: number, now = Date.now()): string {
  if (!timestamp) {
    return 'None yet';
  }
  const seconds = Math.max(0, Math.round((now - timestamp) / 1000));
  if (seconds < 45) {
    return 'Just now';
  }
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) {
    return `${minutes} min ago`;
  }
  const hours = Math.round(minutes / 60);
  if (hours < 24) {
    return `${hours} h ago`;
  }
  return new Date(timestamp).toLocaleDateString();
}

export function formatClockTime(timestamp: number): string {
  if (!timestamp) {
    return '';
  }
  const d = new Date(timestamp);
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${hh}:${mm}`;
}
