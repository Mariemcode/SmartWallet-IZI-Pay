
// models/offer.model.ts
export interface OfferResponse {
  id: number;
  offerCode: string;
  title: string;
  type: string;
  providerName: string;
  category: string;
  cashbackPct: number;
  discountPct: number;
  minAmount: number;
  targetProfiles: string[];
  boost: number;
  description: string;
  status: 'ACTIVE' | 'INACTIVE';
  generationMethod: string;
  generationRun: string;
  createdAt: string;   // ISO date string
  updatedAt: string;
}

export interface OfferRequest {
  title: string;
  type: string;
  providerName?: string;
  category?: string;
  cashbackPct: number;
  discountPct: number;
  minAmount: number;
  targetProfiles: string[];
  boost?: number;
  description?: string;
}

export interface OfferUpdate {
  title?: string;
  type?: string;
  providerName?: string;
  category?: string;
  cashbackPct?: number;
  discountPct?: number;
  minAmount?: number;
  targetProfiles?: string[];
  boost?: number;
  description?: string;
}

export interface OfferStatusUpdate {
  status: 'ACTIVE' | 'INACTIVE';
}

export interface PageResponse<T> {
  offers: T[];
  count: number;
}

// models/recommendation.models.ts (extrait modifié)
export interface ManualRecommendationDTO {
  profileName: string;
  offerCode: string;
  score?: number;
  note?: string;
  description?: string;   // ← Ajout
}

export interface RecommendationStatusDTO {
  status: 'APPROVED' | 'REJECTED';
  note?: string;
}

export interface BulkApproveDTO {
  profileName: string;
}

export interface PageResponse<T> {
  recommendations: T[];
  count: number;
}

export interface RecommendationPageResponse {
  recommendations: RecommendationResponse[];
  count: number;
}
export interface RecommendationResponse {
  id: number;
  profileName: string;
  clusterId: number;
  offerCode: string;
  offerTitle: string;
  offerType: string;
  cashbackPct: number;
  discountPct: number;
  description: string;
  score: number;
  scoreProfile: number;
  scoreProvider: number;
  scoreChurnBoost: number;
  isTargeted: boolean;
  recommendationType: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  adminNote: string;
  generatedAt: string;
  approvedAt: string | null;
  rejectedAt: string | null;
  selected?: boolean; // ✅ Propriété ajoutée pour la sélection batch
}


export interface RecommendationFilters {
  status?: 'PENDING' | 'APPROVED' | 'REJECTED';
  profile?: string;
  offset?: number;
  limit?: number;
}


export interface PipelineHealth {
  status: string;
  components: { name: string; healthy: boolean }[];
  timestamp: string;
}

////////////////////////////////
export interface ApiResponse<T> {
  status: string;
  message: string;
  data: T;
  timestamp: string;
}

export interface MetricsDetailDTO {
  profileName: string;
  precisionScore: number;
  recallScore: number;
  f1Score: number;
  coverage: number;
  acceptanceRate: number;
  avgScore: number;
  nRecommendations: number;
  evaluationType: string;
  computedAt: string;
}

export interface MetricsSummaryDTO {
  avgPrecision: number;
  avgRecall: number;
  avgF1: number;
  evaluationType: string;
  nProfiles: number;
  metrics: MetricsDetailDTO[];
}

// Format brut (snake_case, double encapsulation) pour generation-runs
export interface GenerationRunRaw {
  run_id: string;
  started_at: string;
  finished_at: string | null;
  n_profiles: number;
  n_offers_gen: number;
  n_offers_new: number | null;
  n_offers_deact: number | null;
  status: string;
  error_msg: string | null;
}

export interface GenerationRun {
  runId: string;
  startedAt: string;
  finishedAt: string | null;
  nProfiles: number;
  nOffersGen: number;
  nOffersNew: number;
  nOffersDeact: number;
  status: string;
  errorMsg: string | null;
}

export interface ModelRun {
  modelVersion: string;
  runAt: string;
  silhouetteScore: number;
  daviesBouldin: number;
  calinskiHarabasz: number;
  gbmTestF1: number;
  psiMax: number;
  psiStatus: string;
  nClients: number;
  nMixte: number;
  churnPctHighRisk: number;
}

export interface RecommendationScore {
  profileName: string;
  offerCode: string;
  offerTitle: string;
  score: number;
}

export interface TopOffer {
  offerCode: string;
  offerTitle: string;
  avgScore: number;
  count: number;
  targetProfiles?: string[];
}

export interface HealthAlert {
  type: string;
  profile?: string;
  value: number;
  threshold: number;
  severity: 'warning' | 'critical';
  message: string;
}

// models/client-profile.model.ts
// models/client-profile.model.ts
export interface ClientProfileDTO {
  clientId: string;
  firstName: string;
  lastName: string;
  profileName: string;
  confidenceScore: number;
  churnScore30j: number;
  rfmScore: number;
}