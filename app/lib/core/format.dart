import 'package:intl/intl.dart';

final _date = DateFormat('EEE d MMM');
final _dateTime = DateFormat('d MMM HH:mm');
final _time = DateFormat('HH:mm');

String formatDate(DateTime value) => _date.format(value.toLocal());
String formatDateTime(DateTime value) => _dateTime.format(value.toLocal());
String formatTime(DateTime value) => _time.format(value.toLocal());

DateTime? parseInstant(dynamic value) {
  if (value == null) return null;
  return DateTime.tryParse('$value')?.toLocal();
}

String relativeDay(DateTime value) {
  final now = DateTime.now();
  final a = DateTime(value.year, value.month, value.day);
  final b = DateTime(now.year, now.month, now.day);
  final diff = a.difference(b).inDays;
  if (diff == 0) return 'Today';
  if (diff == 1) return 'Tomorrow';
  if (diff == -1) return 'Yesterday';
  return formatDate(value);
}

String dayName(int dayOfWeek) => const [
      'Monday',
      'Tuesday',
      'Wednesday',
      'Thursday',
      'Friday',
      'Saturday',
      'Sunday',
    ][(dayOfWeek - 1).clamp(0, 6)];
