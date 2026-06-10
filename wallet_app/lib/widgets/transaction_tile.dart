import 'package:flutter/material.dart';
import '../models/dashboard_models.dart';
import '../theme/app_theme.dart';
import '../utils/formatters.dart';

/// Tuile transaction — sans aucun champ technique affiché
class TransactionTile extends StatelessWidget {
  final TransactionItem transaction;
  final VoidCallback?   onTap;

  const TransactionTile({
    super.key,
    required this.transaction,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final category    = transaction.category ?? 'Autre';
    final catColor    = AppTheme.categoryColor(category);
    final amountColor = transaction.credit ? AppTheme.credit : AppTheme.debit;

    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: AppTheme.border, width: 0.5)),
        ),
        child: Row(children: [
          Container(
            width: 44, height: 44,
            decoration: BoxDecoration(
              color: catColor.withOpacity(0.12),
              borderRadius: BorderRadius.circular(13),
            ),
            child: Icon(AppTheme.categoryIcon(category), size: 20, color: catColor),
          ),
          const SizedBox(width: 12),
          Expanded(child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                transaction.typeTitle ?? category,
                style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600,
                    color: AppTheme.textPrimary),
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 2),
              Text(
                _subtitle(),
                style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary),
                overflow: TextOverflow.ellipsis,
              ),
            ],
          )),
          const SizedBox(width: 8),
          Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Text(
              '${transaction.credit ? '+' : '-'} ${Formatters.amountCompact(transaction.amount)}',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700,
                  color: amountColor),
            ),
            const SizedBox(height: 2),
            Text(
              Formatters.dateShort(transaction.transactionDate),
              style: const TextStyle(fontSize: 11, color: AppTheme.textMuted),
            ),
          ]),
        ]),
      ),
    );
  }

  String _subtitle() {
    final parts = <String>[];
    if (transaction.providerName != null) parts.add(transaction.providerName!);
    if (transaction.subCategory != null &&
        transaction.subCategory != transaction.typeTitle)
      parts.add(transaction.subCategory!);
    return parts.isNotEmpty ? parts.join(' · ') : (transaction.category ?? '');
  }
}
