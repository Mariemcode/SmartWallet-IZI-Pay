// provider.model.ts
export interface Provider {
  id: string;
  providerCode: string;    // ← doit correspondre exactement au JSON du backend
  providerName: string;    // ← idem
}

export interface DailyStat {
  date: string;
  count: number;
  totalAmount: number;
}

export interface TransactionTypeStat {
  typeTitle: string;
  category: string;
  subCategory: string;
  typeDirection: string;
  count: number;
  totalAmount: number;
}

export interface TopClient {
  clientId: string;
  firstName: string;
  lastName: string;
  transactionCount: number;
  totalAmount: number;
}

export interface ProviderStats {
  id: string;
  providerCode: string;
  providerName: string;
  totalTransactions: number;
  totalAmount: number;
  avgAmount: number;
  minAmount: number;
  maxAmount: number;
  distinctClients: number;
  reversalCount: number;
  reversalRate: number;
  dailyStats: DailyStat[];
  typeStats: TransactionTypeStat[];
  topClients: TopClient[];
}

// provider.model.ts — ajouter
export interface ProviderSummary {
  id: string;
  providerCode: string;
  providerName: string;
  totalTransactions: number;
  totalAmount: number;
  debitCount: number;
  debitAmount: number;
  creditCount: number;
  creditAmount: number;
  debitPercentCount: number;
  creditPercentCount: number;
  debitPercentAmount: number;
  creditPercentAmount: number;
}

// provider.model.ts — ajouter à la fin
export interface ProviderCard extends ProviderSummary {
  debitArcAmount:  string;
  creditArcAmount: string;
  debitArcCount:   string;
  creditArcCount:  string;
}

export interface ProviderShare {
  id: string;
  providerCode: string;
  providerName: string;
  transactionCount: number;
  totalAmount: number;
  percentCount: number;
  percentAmount: number;
}

export interface ProviderListStats {
  totalProviders: number;
  grandTotalTransactions: number;
  grandTotalAmount: number;
  shares: ProviderShare[];
}

export interface HourlyStat  { hour: number; count: number; }
export interface MonthlyStat { year: number; month: number; count: number; totalAmount: number; }

export interface ProviderDetail {
  id: string;
  providerCode: string;
  providerName: string;
  totalTransactions: number;
  totalAmount: number;
  avgAmount: number;
  minAmount: number;
  maxAmount: number;
  distinctClients: number;
  rankByCount: number;
  rankByAmount: number;
  marketShareCount: number;
  marketShareAmount: number;
  firstTransaction: string;
  lastTransaction: string;
  typeStats: TransactionTypeStat[];
  topClientsByAmount: TopClient[];
  topClientsByCount: TopClient[];
  recurringClients: number;
  occasionalClients: number;
  avgAmountPerClient: number;
  smallCount: number;
  mediumCount: number;
  largeCount: number;
  smallPct: number;
  mediumPct: number;
  largePct: number;
}