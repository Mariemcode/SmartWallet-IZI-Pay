// profile.model.ts

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface HealthDTO {
  status: string;
  model: string;
  loaded: boolean;
  drift: {
    psi_max: number;
    status: string;
    churn_pct_high_risk?: number;
    n_clients?: number;
    last_run?: string;
  };
  timestamp: string;
}

export interface PredictRequestDTO {
  client_id: string;
  features: Record<string, number>;
}

export interface PredictResponseDTO {
  client_id: string;
  cluster_id: number;
  profile_name: string;
  profile_final: string;
  confidence: number;
  is_mixte: boolean;
  churn_score_30j: number;
  churn_segment: string;
  ltv_12m_base: number;
  ltv_12m_optimiste: number;
  ltv_12m_pessimiste: number;
  hazard_rate: number;
  arpu_mensuel: number;
  all_probabilities: Record<string, number>;
  predicted_at: string;
  model_version: string;
}

export interface BatchPredictRequestDTO {
  clients: PredictRequestDTO[];
}

export interface BatchItemResultDTO {
  client_id: string;
  cluster_id: number;
  profile_name: string;
  confidence: number;
  is_mixte: boolean;
  churn_score_30j: number;
  churn_segment: string;
  ltv_12m_base: number;
  ltv_12m_optimiste?: number;
}

export interface BatchPredictResponseDTO {
  results: BatchItemResultDTO[];
  count: number;
  n_mixte: number;
  n_at_risk: number;
  pct_at_risk: number;
  avg_churn: number;
}

export interface ClientProfileDTO {
  client_id: string;
  cluster_id: number;
  profile_name: string;
  profile_final: string;
  is_mixte: boolean;
  confidence_score: number;
  gbm_confidence: number;
  model_version: string;
  total_transactions?: number;
  freq_mensuelle?: number;
  montant_moyen?: number;
  montant_median?: number;
  montant_total?: number;
  regularite?: number;
  rfm_score?: number;
  loyalty_score?: number;
  momentum_court?: number;
  momentum_long?: number;
  recence_jours?: number;
  churn_score_30j?: number;
  churn_segment?: string;
  ltv_12m_base?: number;
  ltv_12m_optimiste?: number;
  ltv_12m_pessimiste?: number;
  hazard_rate?: number;
  arpu_mensuel?: number;
  assigned_at?: string;
  updated_at?: string;
}

export interface ProfileSummaryDTO {
  cluster_id: number;
  profile_name: string;
  description?: string;
  n_clients: number;
  pct_clients: number;
  sil_mean: number;
  sil_min: number;
  is_fragile: boolean;
  dominant_category: string;
  dominant_category_ratio: number;
  dominant_category_vs_global?: number;
  secondary_category: string;
  activity_level: string;
  freq_mensuelle_mean?: number;
  montant_moyen_mean?: number;
  montant_total_mean?: number;
  regularite_mean?: number;
  rfm_score_mean?: number;
  loyalty_score_mean?: number;
  momentum_court_mean?: number;
  momentum_long_mean?: number;
  recence_jours_mean?: number;
  churn_score_30j?: number;
  churn_pct_high_risk?: number;
  arpu_mensuel?: number;
  ltv_12m_base?: number;
  ltv_12m_optimiste?: number;
  ltv_12m_pessimiste?: number;
  taux_activation?: number;
  score_risque?: number;
  growth_rate_3m?: number;
  hazard_rate?: number;
}

export interface KpiSummaryDTO {
  cluster_id: number;
  profile_name: string;
  churn_score_30j: number;
  churn_pct_high_risk: number;
  arpu_mensuel: number;
  ltv_12m_base: number;
  score_risque: number;
}

export interface DriftStatusDTO {
  model_version: string;
  psi_max: number;
  psi_status: string;
  gbm_accuracy: number;
  gbm_test_f1: number;
  gbm_holdout_f1?: number;
  churn_pct_high_risk: number;
  fragile_profiles: number[];
  run_at: string;
}

export interface MigrationSummaryDTO {
  cluster_id_old: number;
  cluster_id_new: number;
  profile_name_old: string;
  profile_name_new: string;
  nb_migrations: number;
  first_migration: string;
  last_migration: string;
}

export interface ProfileTransactionsCountDTO {
  cluster_id: number;
  total_transactions: number;
  avg_transactions: number;
}

export interface ProfileCategoryDTO {
  category: string;
  nb_transactions: number;
  pct: number;
}

export interface ModelRunDTO {
  model_version: string;
  psi_max: number;
  psi_status: string;
  gbm_accuracy: number;
  gbm_test_f1: number;
  gbm_holdout_f1?: number;
  churn_pct_high_risk: number;
  fragile_profiles: number[];
  run_at: string;
}

export interface RetrainStatusDTO {
  running: boolean;
  last_run?: string;
  last_result?: string;
  triggered_by?: string;
}

export interface MonitoringAlertDTO {
  id: number;
  alert_type: string;
  feature_name: string;
  psi_value: number;
  ks_pvalue: number;
  severity: string;
  message: string;
  model_version: string;
  triggered_at: string;
  resolved: boolean;
}

// ========== PAGINATION & CLIENT ==========
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;          // page courante (base 0)
  first: boolean;
  last: boolean;
  empty: boolean;
  numberOfElements?: number;
  pageable?: any;
  sort?: any;
}

export interface ClientWithProfileDTO {
  clientId: string;
  firstName: string;
  lastName: string;
  clusterId: number;
  profileName: string;
  profileFinal: string;
  confidenceScore: number;
  churnScore30j: number;
  churnSegment: string;
}