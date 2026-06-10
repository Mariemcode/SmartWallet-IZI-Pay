// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//  SmartWallet Admin IA — Modèles TypeScript
//  Cohérents avec les DTOs backend de client_bd
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// ── Santé du service ML ───────────────────────────────────────
export interface MlHealthStatus {
  status:          'UP' | 'DEGRADED' | 'DOWN' | string;
  version:         string;
  model_age_hours: number | null;
  needs_retrain:   boolean;
  models_loaded:   boolean;
  modules: {
    m1_factures:  boolean;
    m2_recharge:  boolean;
    m3_budget:    boolean;
    m4_habitudes: boolean;
  };
  coverage: {
    gold_clients:      number;
    moyen_clients:     number;
    recharge_clients:  number;
    factures_entries:  number;
  };
  retrain: {
    status:       string;
    last_success: string | null;
    last_error:   string | null;
    trigger:      string | null;
  };
  timestamp: string;
}

// ── Métriques modèle facture ──────────────────────────────────
export interface BillModelRaw {
  mae:  number;
  rmse: number;
  r2:   number;
}

export interface BillModelMetrics extends BillModelRaw {
  label:           string;
  r2Ok:            boolean;
  r2Threshold:     string;
  confidenceLabel: string;
}

const THRESHOLDS: Record<string, number> = {
  TOPNET: 0.99, BEE: 0.99,
  SONEDE: 0.70, STEG: 0.70,
  TT: 0.65,    OOREDOO: 0.65,
};

export function enrichMetrics(label: string, raw: BillModelRaw): BillModelMetrics {
  const threshold = THRESHOLDS[label] ?? 0.60;
  const r2Ok = raw.r2 >= threshold;
  const r2Threshold = `≥ ${threshold.toFixed(2)}`;
  let confidenceLabel: string;
  if (['TOPNET','BEE'].includes(label))        confidenceLabel = r2Ok ? 'Haute'   : 'Dégradé';
  else if (['SONEDE','STEG'].includes(label))  confidenceLabel = r2Ok ? 'Moyenne' : 'Dégradé';
  else                                          confidenceLabel = r2Ok ? 'Faible'  : 'Insuffisant';
  return { ...raw, label, r2Ok, r2Threshold, confidenceLabel };
}

export function getThreshold(label: string): number {
  return THRESHOLDS[label] ?? 0.60;
}

// ── Métriques complètes ───────────────────────────────────────
export interface MlMetrics {
  module1_factures:    Record<string, BillModelRaw>;
  degradation_alerts:  string[];
  model_healthy:       boolean;
  retrain_status:      string;
  last_retrain:        string | null;
  timestamp:           string;
  billMetrics?:        Record<string, BillModelMetrics>;
}

// ── État du réentraînement ────────────────────────────────────
export interface RetrainStatus {
  status:        'idle' | 'running' | 'success' | 'failed' | string;
  last_run:      string | null;
  last_success:  string | null;
  last_error:    string | null;
  duration_sec:  number | null;
  trigger:       string | null;
  pkls_updated:  string[];
  timestamp?:    string;
  last_retrain_status?: string;
  fastapi_health?:      string;
}

// ── Métadonnées Module 6 (Recommandations IA) ────────────────
export interface RecoMeta {
  n_clients:          number;
  n_clusters:         number;
  cluster_names:      Record<string, string>;
  n_recommendations:  number;
  n_alerts:           number;
  n_budget_optim:     number;
  silhouette_score:   number;
  total_savings_tnd:  number;
  generated_at:       string;
}

// ── Notification ─────────────────────────────────────────────
export interface NotificationLog {
  id:             string;
  client_id:      string;
  titre:          string;
  message:        string;
  type:           string;
  sent_at:        string;
  status:         string;
  date_planifiee: string | null;
}

// ── Reward (cohérent avec entity Reward de client_bd) ────────
export interface Reward {
  id:           string;
  titre:        string;
  description:  string;
  type:         'CASHBACK' | 'POINTS' | 'REDUCTION' | string;
  valeur_tnd:   number;
  points_requis: number;
  actif:        boolean;
  created_at?:  string;
}

// ── Alerte de monitoring IA ───────────────────────────────────
export interface MlAlert {
  id:           string;
  feature_name: string;
  severity:     'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
  message:      string;
  model_version: string;
  triggered_at: string;
  resolved:     boolean;
}
