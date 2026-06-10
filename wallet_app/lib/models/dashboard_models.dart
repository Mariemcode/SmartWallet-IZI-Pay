// ═══════════════════════════════════════════════════════════════════
//  MODÈLES FLUTTER — mappés exactement sur les DTOs du backend v1
//  (smartwallet-dashboard.zip — aucune modification backend)
//
//  Masquage appliqué UNIQUEMENT côté UI Flutter :
//    - id, typeCode, typeOriginalTitle, reversalFlag, receiverId
//      → reçus du backend mais jamais affichés à l'écran
//    - Frais & Commissions, Annulation & Correction
//      → filtrés localement avant affichage
// ═══════════════════════════════════════════════════════════════════

// ── Catégories cachées (filtrées côté Flutter) ────────────────────
const Set<String> _hiddenCategories = {
  'Frais & Commissions',
  'Annulation & Correction',
};

bool isCategoryVisible(String? category) =>
    category != null && !_hiddenCategories.contains(category);

// ── 1. Résumé wallet ─────────────────────────────────────────────────
class WalletSummary {
  final double  balance;
  final double  spentThisMonth;
  final double  receivedThisMonth;
  final int     totalTransactions;
  final int     transactionsThisMonth;
  final String  currency;
  final double? spentVariationPercent;

  const WalletSummary({
    required this.balance,
    required this.spentThisMonth,
    required this.receivedThisMonth,
    required this.totalTransactions,
    required this.transactionsThisMonth,
    required this.currency,
    this.spentVariationPercent,
  });

  factory WalletSummary.fromJson(Map<String, dynamic> j) => WalletSummary(
    balance:               (j['balance'] as num).toDouble(),
    spentThisMonth:        (j['spentThisMonth'] as num).toDouble(),
    receivedThisMonth:     (j['receivedThisMonth'] as num).toDouble(),
    totalTransactions:     j['totalTransactions'] as int,
    transactionsThisMonth: j['transactionsThisMonth'] as int,
    currency:              j['currency'] as String? ?? 'TND',
    spentVariationPercent: (j['spentVariationPercent'] as num?)?.toDouble(),
  );
}

// ── 2. Transaction (liste historique) ────────────────────────────────
// id gardé en mémoire uniquement pour la navigation vers le détail
// reversalFlag, typeCode, typeOriginalTitle → reçus mais jamais affichés
class TransactionItem {
  final String      id;          // navigation API uniquement, pas affiché
  final double      amount;
  final String      currency;
  final DateTime    transactionDate;
  final String      flowType;
  final bool        credit;
  final String?     typeTitle;
  final String?     category;
  final String?     subCategory;
  final String?     providerName;
  // reversalFlag reçu du backend mais ignoré côté UI
  // (les transactions R sont exclues à la source dans le backend v1)

  const TransactionItem({
    required this.id,
    required this.amount,
    required this.currency,
    required this.transactionDate,
    required this.flowType,
    required this.credit,
    this.typeTitle,
    this.category,
    this.subCategory,
    this.providerName,
  });

  factory TransactionItem.fromJson(Map<String, dynamic> j) => TransactionItem(
    id:              j['id'] as String,
    amount:          (j['amount'] as num).toDouble(),
    currency:        j['currency'] as String? ?? 'TND',
    transactionDate: DateTime.parse(j['transactionDate'] as String),
    flowType:        j['flowType'] as String? ?? 'D',
    credit:          j['credit'] as bool? ?? false,
    typeTitle:       j['typeTitle'] as String?,
    category:        j['category'] as String?,
    subCategory:     j['subCategory'] as String?,
    providerName:    j['providerName'] as String?,
    // reversalFlag ignoré intentionnellement
  );
}

// ── 3. Page historique ────────────────────────────────────────────────
class TransactionPage {
  final List<TransactionItem> transactions;
  final int totalElements;
  final int totalPages;
  final int currentPage;
  final int pageSize;

  const TransactionPage({
    required this.transactions,
    required this.totalElements,
    required this.totalPages,
    required this.currentPage,
    required this.pageSize,
  });

  factory TransactionPage.fromJson(Map<String, dynamic> j) => TransactionPage(
    transactions:  (j['transactions'] as List)
        .map((e) => TransactionItem.fromJson(e as Map<String, dynamic>))
        .where((tx) => isCategoryVisible(tx.category)) // filtre local
        .toList(),
    totalElements: j['totalElements'] as int,
    totalPages:    j['totalPages'] as int,
    currentPage:   j['currentPage'] as int,
    pageSize:      j['pageSize'] as int,
  );
}

// ── 4. Détail transaction ─────────────────────────────────────────────
// Champs reçus du backend v1 mais NON affichés :
//   id, typeCode, typeOriginalTitle, reversalFlag, receiverId (UUID brut)
class TransactionDetail {
  final double      amount;
  final String      currency;
  final DateTime    transactionDate;
  final String      flowType;
  final bool        credit;
  final String?     typeTitle;
  final String?     category;
  final String?     subCategory;
  final String?     providerName;
  // receiverId : UUID brut → converti en label lisible pour l'affichage
  final bool        hasReceiver;
  final bool        isCredit;

  const TransactionDetail({
    required this.amount,
    required this.currency,
    required this.transactionDate,
    required this.flowType,
    required this.credit,
    this.typeTitle,
    this.category,
    this.subCategory,
    this.providerName,
    required this.hasReceiver,
    required this.isCredit,
  });

  factory TransactionDetail.fromJson(Map<String, dynamic> j) {
    final credit = j['credit'] as bool? ?? false;
    return TransactionDetail(
      amount:          (j['amount'] as num).toDouble(),
      currency:        j['currency'] as String? ?? 'TND',
      transactionDate: DateTime.parse(j['transactionDate'] as String),
      flowType:        j['flowType'] as String? ?? 'D',
      credit:          credit,
      typeTitle:       j['typeTitle'] as String?,
      category:        j['category'] as String?,
      subCategory:     j['subCategory'] as String?,
      providerName:    j['providerName'] as String?,
      hasReceiver:     j['receiverId'] != null,
      isCredit:        credit,
      // id, typeCode, typeOriginalTitle, reversalFlag, receiverId → ignorés
    );
  }

  /// Label humain pour le destinataire P2P (jamais l'UUID brut)
  String? get receiverLabel {
    if (!hasReceiver) return null;
    return isCredit ? 'Portefeuille expéditeur' : 'Portefeuille destinataire';
  }
}

// ── 5. Répartition catégorie ──────────────────────────────────────────
class CategoryBreakdown {
  final String category;
  final double totalAmount;
  final int    count;
  final double percentage;

  const CategoryBreakdown({
    required this.category,
    required this.totalAmount,
    required this.count,
    required this.percentage,
  });

  factory CategoryBreakdown.fromJson(Map<String, dynamic> j) => CategoryBreakdown(
    category:    j['category'] as String,
    totalAmount: (j['totalAmount'] as num).toDouble(),
    count:       j['count'] as int,
    percentage:  (j['percentage'] as num).toDouble(),
  );
}

// ── 6. Évolution mensuelle ────────────────────────────────────────────
class MonthlyEvolution {
  final String monthLabel;
  final double totalCredit;
  final double totalDebit;
  final double netBalance;

  const MonthlyEvolution({
    required this.monthLabel,
    required this.totalCredit,
    required this.totalDebit,
    required this.netBalance,
  });

  factory MonthlyEvolution.fromJson(Map<String, dynamic> j) => MonthlyEvolution(
    monthLabel:  j['monthLabel'] as String,
    totalCredit: (j['totalCredit'] as num).toDouble(),
    totalDebit:  (j['totalDebit'] as num).toDouble(),
    netBalance:  (j['netBalance'] as num).toDouble(),
  );
}

// ── 7. Évolution hebdomadaire ─────────────────────────────────────────
class WeeklyEvolution {
  final String dayLabel;
  final String date;
  final double totalDebit;
  final double totalCredit;

  const WeeklyEvolution({
    required this.dayLabel,
    required this.date,
    required this.totalDebit,
    required this.totalCredit,
  });

  factory WeeklyEvolution.fromJson(Map<String, dynamic> j) => WeeklyEvolution(
    dayLabel:    j['dayLabel'] as String,
    date:        j['date'] as String,
    totalDebit:  (j['totalDebit'] as num).toDouble(),
    totalCredit: (j['totalCredit'] as num).toDouble(),
  );
}

// ── 8. Top catégories ─────────────────────────────────────────────────
class TopCategory {
  final int    rank;
  final String category;
  final double totalAmount;
  final int    transactionCount;
  final double percentageOfTotal;

  const TopCategory({
    required this.rank,
    required this.category,
    required this.totalAmount,
    required this.transactionCount,
    required this.percentageOfTotal,
  });

  factory TopCategory.fromJson(Map<String, dynamic> j) => TopCategory(
    rank:              j['rank'] as int,
    category:          j['category'] as String,
    totalAmount:       (j['totalAmount'] as num).toDouble(),
    transactionCount:  j['transactionCount'] as int,
    percentageOfTotal: (j['percentageOfTotal'] as num).toDouble(),
  );
}

// ── 9. Statistiques période ───────────────────────────────────────────
class PeriodStats {
  final String period;
  final double avgTransactionAmount;
  final double maxTransaction;
  final double minTransaction;
  final int    activeDays;
  final double avgDailySpend;

  const PeriodStats({
    required this.period,
    required this.avgTransactionAmount,
    required this.maxTransaction,
    required this.minTransaction,
    required this.activeDays,
    required this.avgDailySpend,
  });

  factory PeriodStats.fromJson(Map<String, dynamic> j) => PeriodStats(
    period:               j['period'] as String,
    avgTransactionAmount: (j['avgTransactionAmount'] as num).toDouble(),
    maxTransaction:       (j['maxTransaction'] as num).toDouble(),
    minTransaction:       (j['minTransaction'] as num).toDouble(),
    activeDays:           j['activeDays'] as int,
    avgDailySpend:        (j['avgDailySpend'] as num).toDouble(),
  );
}

// ── 10. Flux catégorie ────────────────────────────────────────────────
class CategoryFlow {
  final String category;
  final double creditAmount;
  final double debitAmount;
  final int    creditCount;
  final int    debitCount;

  const CategoryFlow({
    required this.category,
    required this.creditAmount,
    required this.debitAmount,
    required this.creditCount,
    required this.debitCount,
  });

  factory CategoryFlow.fromJson(Map<String, dynamic> j) => CategoryFlow(
    category:     j['category'] as String,
    creditAmount: (j['creditAmount'] as num).toDouble(),
    debitAmount:  (j['debitAmount'] as num).toDouble(),
    creditCount:  j['creditCount'] as int,
    debitCount:   j['debitCount'] as int,
  );
}

// ── 11. Profil client ─────────────────────────────────────────────────
// id reçu du backend mais non exposé dans l'UI
class ClientProfile {
  final String?   firstName;
  final String?   lastName;
  final String    phoneNumber;
  final DateTime? memberSince;
  final String?   topSpendingCategory;
  final int       totalTransactions;

  const ClientProfile({
    this.firstName,
    this.lastName,
    required this.phoneNumber,
    this.memberSince,
    this.topSpendingCategory,
    required this.totalTransactions,
  });

  factory ClientProfile.fromJson(Map<String, dynamic> j) => ClientProfile(
    firstName:           j['firstName'] as String?,
    lastName:            j['lastName'] as String?,
    phoneNumber:         j['phoneNumber'] as String,
    memberSince:         j['memberSince'] != null
        ? DateTime.tryParse(j['memberSince'] as String)
        : null,
    topSpendingCategory: j['topSpendingCategory'] as String?,
    totalTransactions:   j['totalTransactions'] as int,
    // id ignoré intentionnellement
  );

  String get displayName {
    if (firstName != null && lastName != null) return '$firstName $lastName';
    if (firstName != null) return firstName!;
    return phoneNumber;
  }
}

// ── Enum période ──────────────────────────────────────────────────────
enum DashboardPeriod {
  thisMonth('THIS_MONTH',     'Ce mois'),
  lastMonth('LAST_MONTH',     'Mois dernier'),
  last3Months('LAST_3_MONTHS','3 mois'),
  last6Months('LAST_6_MONTHS','6 mois'),
  thisYear('THIS_YEAR',       'Cette année'),
  all('ALL',                  'Tout');

  final String value;
  final String label;
  const DashboardPeriod(this.value, this.label);
}
