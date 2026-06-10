export interface Transaction {
  id: string;
  clientId: string;

  amount: number;
  currency: string;
  transactionDate: string;       // ← corrigé (était transactionDateTime)
  reversalFlag: string;          // "Y" ou "N"

  // Provider (nullable)
  providerCode?: string;
  providerName?: string;

  // Type transaction
  typeCode?: string;
  typeTitle?: string;
  typeOriginalTitle?: string;
  typeCategory?: string;
  typeSubCategory?: string;
  typeType?: string;             // "C" ou "D"
}