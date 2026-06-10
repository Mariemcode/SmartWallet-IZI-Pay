import 'package:intl/intl.dart';

class Formatters {
  static final _currency = NumberFormat('#,##0.000', 'fr_TN');
  static final _compact  = NumberFormat('#,##0.00', 'fr_TN');
  static final _dateShort = DateFormat('dd MMM yyyy', 'fr');
  static final _dateLong  = DateFormat("dd MMMM yyyy 'à' HH:mm", 'fr');
  static final _dateTime  = DateFormat('HH:mm', 'fr');

  static String amount(double value) => '${_currency.format(value)} TND';
  static String amountCompact(double value) => '${_compact.format(value)} TND';
  static String dateShort(DateTime dt) => _dateShort.format(dt);
  static String dateLong(DateTime dt) => _dateLong.format(dt);
  static String timeOnly(DateTime dt) => _dateTime.format(dt);

  static String variation(double? pct) {
    if (pct == null) return '-';
    final sign = pct >= 0 ? '+' : '';
    return '$sign${pct.toStringAsFixed(1)}%';
  }

  /// "2024-03" → "Mar 2024"
  static String monthLabel(String label) {
    try {
      final parts = label.split('-');
      final dt = DateTime(int.parse(parts[0]), int.parse(parts[1]));
      return DateFormat('MMM yy', 'fr').format(dt);
    } catch (_) {
      return label;
    }
  }
}
