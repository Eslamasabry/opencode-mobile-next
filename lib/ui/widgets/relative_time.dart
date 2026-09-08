/// Short relative age for list rows: "Just now", "5m ago", "3h ago",
/// "2d ago", then an absolute date. Shared so every session list reads the
/// same distance to "now".
String relativeTimeLabel(int milliseconds, {DateTime? now}) {
  final date = DateTime.fromMillisecondsSinceEpoch(milliseconds);
  final difference = (now ?? DateTime.now()).difference(date);
  if (difference.inMinutes < 1) return 'Just now';
  if (difference.inHours < 1) return '${difference.inMinutes}m ago';
  if (difference.inDays < 1) return '${difference.inHours}h ago';
  if (difference.inDays < 7) return '${difference.inDays}d ago';
  final month = date.month.toString().padLeft(2, '0');
  final day = date.day.toString().padLeft(2, '0');
  return '${date.year}-$month-$day';
}
