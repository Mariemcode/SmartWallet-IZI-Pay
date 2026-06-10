export interface DashboardKpi {
  totalTransactions: number;
  totalAmount:       number;
  avgAmount:         number;
  totalClients:      number;
  totalProviders:    number;
}

export interface DailyActivity {
  date:        string;
  count:       number;
  totalAmount: number;
}

export interface DebitCredit {
  debitCount:      number;
  debitAmount:     number;
  debitPctCount:   number;
  debitPctAmount:  number;
  creditCount:     number;
  creditAmount:    number;
  creditPctCount:  number;
  creditPctAmount: number;
  avgDebit:        number;
  avgCredit:       number;
}

export interface TopProvider {
  id:               string;
  providerCode:     string;
  providerName:     string;
  transactionCount: number;
  totalAmount:      number;
  percentCount:     number;
  percentAmount:    number;
}

export interface TopCategory {
  category:         string;
  transactionCount: number;
  totalAmount:      number;
  percentCount:     number;
  percentAmount:    number;
}

export interface DashboardResponse {
  kpi:           DashboardKpi;
  dailyActivity: DailyActivity[];
  debitCredit:   DebitCredit;
  topProviders:  TopProvider[];
  topCategories: TopCategory[];
}