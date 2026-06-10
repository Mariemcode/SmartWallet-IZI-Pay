import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription, interval } from 'rxjs';
import {
  MetricsSummaryDTO, MetricsDetailDTO, HealthAlert, TopOffer, GenerationRun
} from '../../models/recommendation.models';
import { OfferService } from '../../services/recommendation/offre/offer.service';
import { RecommendationmetricsService } from '../../services/recommendation/RecommendationMetrics/recommendationmetrics.service';
import { IaAdminService } from '../../services/ia-admin/ia-admin.service';

/* ─────────────────────────────────────────────────────────────
   Interfaces Marketing Feedback (fusionnées depuis MarketingFeedbackComponent)
   ───────────────────────────────────────────────────────────── */
interface OfferStatsRow {
  offer_code: string;
  offer_title: string;
  offer_type?: string;
  boost?: number;
  total: number;
  sent: number;
  opened: number;
  accepted: number;
  rejected: number;
  accept_rate: number | null;
  open_rate: number | null;
}

interface ProfileStatsRow {
  cluster_id: number;
  profile_name: string;
  total: number;
  unique_clients: number;
  opened: number;
  accepted: number;
  rejected: number;
  accept_rate: number | null;
}

interface InteractionRow {
  id: number;
  client_id: string;
  offer_code: string;
  offer_title?: string;
  action: string;
  recorded_at: string;
}

interface RecoRow {
  id: number;
  client_id: string;
  offer_code: string;
  offer_title?: string;
  profile_name?: string;
  cluster_id?: number;
  status: string;
  sent_at?: string;
  opened_at?: string;
  accepted_at?: string;
  rejected_at?: string;
}

interface AoaRow {
  id: number;
  client_id: string;
  offer_code: string;
  effect_type: string;
  status: string;
  starts_at?: string;
  ends_at?: string;
  applied_amount_total?: string;
  created_at: string;
}

type MainTab =
  | 'model'         // vue d'origine RecommendationModel
  | 'overview'
  | 'offers'
  | 'profiles'
  | 'interactions'
  | 'recos'
  | 'applications';

@Component({
  selector: 'app-recommendationmodel',
  templateUrl: './recommendationmodel.component.html',
  styleUrl: './recommendationmodel.component.css'
})
export class RecommendationmodelComponent implements OnInit, OnDestroy {

  /* ═══════════════════════════════════════════════════════════
     ÉTAT — partie RecommendationModel (inchangé)
     ═══════════════════════════════════════════════════════════ */
  evaluationType: 'simulated' | 'real' = 'simulated';
  loading = false;
  regeneratingOffers = false;
  regeneratingRecos = false;
  notificationMessage: string | null = null;
  notificationType: 'success' | 'error' = 'success';

  metricsSummary: MetricsSummaryDTO | null = null;
  metricsList: MetricsDetailDTO[] = [];
  alerts: HealthAlert[] = [];
  topOffers: TopOffer[] = [];

  offerGenerationRuns: GenerationRun[] = [];
  recommendationGenerationRuns: GenerationRun[] = [];

  historyModalOpen = false;
  selectedProfile = '';
  historyMetrics: MetricsDetailDTO[] = [];

  /* ═══════════════════════════════════════════════════════════
     ÉTAT — partie Marketing Feedback (fusionnée)
     ═══════════════════════════════════════════════════════════ */
  // navigation onglets — 'model' = écran d'origine
  activeTab: MainTab = 'model';

  // chargement
  loadingDashboard = false;
  loadingOffers = false;
  loadingProfiles = false;
  loadingInter = false;
  loadingRecos = false;
  loadingAoa = false;
  pushingFeedback = false;
  retraining = false;
  resetting = false;

  // KPIs marketing
  totalSent = 0;
  totalOpened = 0;
  totalAccepted = 0;
  totalRejected = 0;
  totalPending = 0;
  uniqueClients = 0;
  acceptRate = 0;
  openRate = 0;
  activeApps = 0;
  totalInteractions = 0;
  bufferSizeFastApi = 0;
  schedulerCursor = '—';

  // listes marketing
  offerStats: OfferStatsRow[] = [];
  profileStats: ProfileStatsRow[] = [];
  interactions: InteractionRow[] = [];
  recos: RecoRow[] = [];
  aoa: AoaRow[] = [];

  maxOfferVolume = 1;
  maxProfileVolume = 1;

  // filtres
  filterStatus = '';
  filterAction = '';
  filterOfferCode = '';

  // feedback UI marketing (utilise le toast existant)
  lastPushResult: { items_sent: number; last_cursor: string } | null = null;
  lastRetrainResult: any = null;

  // auto-refresh
  autoRefresh = false;
  private autoRefreshSub?: Subscription;

  constructor(
    private metricsService: RecommendationmetricsService,
    private offerService: OfferService,
    private iaAdmin: IaAdminService
  ) {}

  ngOnInit(): void {
    this.loadAllData();
    this.loadHealthAlerts();
    this.loadMarketingAll();
  }

  ngOnDestroy(): void {
    this.autoRefreshSub?.unsubscribe();
  }

  /* ═══════════════════════════════════════════════════════════
     NAVIGATION ONGLETS
     ═══════════════════════════════════════════════════════════ */
  setTab(t: MainTab): void {
    this.activeTab = t;
  }

  /* ═══════════════════════════════════════════════════════════
     CHARGEMENT — RecommendationModel (inchangé)
     ═══════════════════════════════════════════════════════════ */
  loadAllData(): void {
    this.loading = true;
    this.clearNotification();

    console.log('🔄 [RecoModel] Chargement données...');

    this.metricsService.getMetrics(this.evaluationType).subscribe({
      next: (data) => {
        console.log('✅ Métriques:', data);
        this.metricsSummary = data;
        this.metricsList = data.metrics || [];
        this.loadRecommendationsData();
      },
      error: (err) => console.error('❌ Métriques:', err)
    });

    this.metricsService.getOfferGenerationRuns(20).subscribe({
      next: (runs) => {
        console.log('✅ Offer runs:', runs);
        this.offerGenerationRuns = runs.runs || [];
      },
      error: (err) => console.error('❌ Offer runs:', err)
    });

    this.metricsService.getRecommendationGenerationRuns(20).subscribe({
      next: (runs) => {
        console.log('✅ Reco runs:', runs);
        this.recommendationGenerationRuns = runs.runs || [];
      },
      error: (err) => console.error('❌ Reco runs:', err)
    });
  }

  loadHealthAlerts(): void {
    this.metricsService.getHealthAlerts().subscribe({
      next: (alerts) => this.alerts = alerts,
      error: (err) => console.error('Erreur alertes santé', err)
    });
  }

  loadRecommendationsData(): void {
    this.metricsService.getRecommendations(undefined, 500).subscribe({
      next: (recs) => {
        if (recs.length === 0) {
          this.topOffers = [];
          this.loading = false;
          return;
        }
        // Récupérer les titres complets depuis le service d'offres
        this.offerService.listOffers({ status: 'ACTIVE', limit: 100 }).subscribe({
          next: (page) => {
            const titleMap = new Map<string, string>();
            const targetMap = new Map<string, string[]>();
            page.offers.forEach(offer => {
              titleMap.set(offer.offerCode, offer.title);
              targetMap.set(offer.offerCode, offer.targetProfiles || []);
            });
            const offerMap = new Map<string, { sum: number; count: number; title: string; targets: string[] }>();
            recs.forEach(r => {
              const realTitle = titleMap.get(r.offerCode) || r.offerTitle || 'Titre inconnu';
              const targets = targetMap.get(r.offerCode) || [];
              const existing = offerMap.get(r.offerCode);
              if (existing) {
                existing.sum += r.score;
                existing.count++;
              } else {
                offerMap.set(r.offerCode, { sum: r.score, count: 1, title: realTitle, targets });
              }
            });
            this.topOffers = Array.from(offerMap.entries())
              .map(([code, { sum, count, title, targets }]) => ({
                offerCode: code,
                offerTitle: title,
                avgScore: sum / count,
                count: count,
                targetProfiles: targets
              }))
              .sort((a, b) => b.avgScore - a.avgScore)
              .slice(0, 5);
            this.loading = false;
          },
          error: (err) => {
            console.error('Erreur chargement offres', err);
            // Fallback sans titres complets
            const offerMap = new Map();
            recs.forEach(r => {
              const existing = offerMap.get(r.offerCode);
              if (existing) {
                existing.sum += r.score;
                existing.count++;
              } else {
                offerMap.set(r.offerCode, { sum: r.score, count: 1, title: r.offerTitle || 'Sans titre', targets: [] });
              }
            });
            this.topOffers = Array.from(offerMap.entries())
              .map(([code, { sum, count, title, targets }]) => ({
                offerCode: code,
                offerTitle: title,
                avgScore: sum / count,
                count: count,
                targetProfiles: targets
              }))
              .sort((a, b) => b.avgScore - a.avgScore)
              .slice(0, 5);
            this.loading = false;
          }
        });
      },
      error: (err) => {
        console.error('Erreur chargement recommandations:', err);
        this.loading = false;
      }
    });
  }

  regenerateOffers(): void {
    if (this.regeneratingOffers) return;
    this.regeneratingOffers = true;
    this.clearNotification();
    this.metricsService.regenerateOffers().subscribe({
      next: () => {
        this.showNotification('Génération des offres lancée. Mise à jour dans 10 secondes...', 'success');
        setTimeout(() => this.refreshAfterRegeneration('offers'), 10000);
      },
      error: (err) => {
        console.error(err);
        this.showNotification('Erreur lors du lancement de la génération des offres', 'error');
        this.regeneratingOffers = false;
      }
    });
  }

  regenerateRecommendations(): void {
    if (this.regeneratingRecos) return;
    this.regeneratingRecos = true;
    this.clearNotification();
    this.metricsService.regenerateRecommendations().subscribe({
      next: () => {
        this.showNotification('Génération des recommandations lancée. Mise à jour dans 10 secondes...', 'success');
        setTimeout(() => this.refreshAfterRegeneration('recos'), 10000);
      },
      error: (err) => {
        console.error(err);
        this.showNotification('Erreur lors du lancement de la génération des recommandations', 'error');
        this.regeneratingRecos = false;
      }
    });
  }

  private refreshAfterRegeneration(type: string): void {
    this.loadAllData();
    this.loadHealthAlerts();
    setTimeout(() => {
      const todayStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
      const hasNewOffers = this.offerGenerationRuns.some(r => r.runId.includes(todayStr));
      const hasNewRecos = this.recommendationGenerationRuns.some(r => r.runId.includes(todayStr));
      if ((type === 'offers' && hasNewOffers) || (type === 'recos' && hasNewRecos)) {
        this.showNotification('Régénération terminée et visible dans le tableau.', 'success');
      } else {
        this.showNotification('Régénération terminée, mais les nouveaux runs ne sont pas encore affichés. Vérifiez la base de données.', 'error');
      }
      this.regeneratingOffers = false;
      this.regeneratingRecos = false;
    }, 3000);
  }

  public clearNotification(): void {
    this.notificationMessage = null;
  }

  private showNotification(message: string, type: 'success' | 'error'): void {
    this.notificationMessage = message;
    this.notificationType = type;
    setTimeout(() => {
      if (this.notificationMessage === message) this.clearNotification();
    }, 8000);
  }

  onEvaluationChange(): void {
    this.loadAllData();
    this.loadHealthAlerts();
  }

  showHistory(profile: string): void {
    this.selectedProfile = profile;
    this.metricsService.getMetricsHistory(profile, 10).subscribe({
      next: (history) => {
        this.historyMetrics = history.metrics || [];
        this.historyModalOpen = true;
      },
      error: (err) => console.error(err)
    });
  }

  closeHistory(): void {
    this.historyModalOpen = false;
    this.selectedProfile = '';
    this.historyMetrics = [];
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleString();
  }

  getSeverityClass(severity: string): string {
    return severity === 'critical' ? 'alert-critical' : 'alert-warning';
  }

  /* ═══════════════════════════════════════════════════════════
     CHARGEMENT — Marketing Feedback (fusionné)
     ═══════════════════════════════════════════════════════════ */
  loadMarketingAll(): void {
    this.loadDashboard();
    this.loadOffersStats();
    this.loadProfilesStats();
    this.loadInteractions();
    this.loadRecos();
    this.loadAoa();
    this.loadFastApiStats();
  }

  loadDashboard(): void {
    this.loadingDashboard = true;
    this.iaAdmin.getMarketingDashboard().subscribe({
      next: (r: any) => {
        const k = r?.kpis ?? {};
        this.totalSent = k.total_sent ?? 0;
        this.totalOpened = k.total_opened ?? 0;
        this.totalAccepted = k.total_accepted ?? 0;
        this.totalRejected = k.total_rejected ?? 0;
        this.totalPending = k.total_pending ?? 0;
        this.uniqueClients = k.unique_clients ?? 0;
        this.acceptRate = k.accept_rate ?? 0;
        this.openRate = k.open_rate ?? 0;
        this.activeApps = k.active_applications ?? 0;
        this.totalInteractions = k.total_interactions ?? 0;
        this.schedulerCursor = r?.scheduler_cursor ?? '—';
        this.loadingDashboard = false;
      },
      error: (e) => {
        this.showNotification(`Dashboard : ${e?.error?.error || e?.message || 'erreur réseau'}`, 'error');
        this.loadingDashboard = false;
      }
    });
  }

  loadOffersStats(): void {
    this.loadingOffers = true;
    this.iaAdmin.getOffersStats().subscribe({
      next: (r: any) => {
        this.offerStats = r?.data ?? [];
        this.maxOfferVolume = Math.max(1, ...this.offerStats.map(o => o.total));
        this.loadingOffers = false;
      },
      error: () => { this.offerStats = []; this.loadingOffers = false; }
    });
  }

  loadProfilesStats(): void {
    this.loadingProfiles = true;
    this.iaAdmin.getProfilesStats().subscribe({
      next: (r: any) => {
        this.profileStats = r?.data ?? [];
        this.maxProfileVolume = Math.max(1, ...this.profileStats.map(p => p.total));
        this.loadingProfiles = false;
      },
      error: () => { this.profileStats = []; this.loadingProfiles = false; }
    });
  }

  loadInteractions(): void {
    this.loadingInter = true;
    this.iaAdmin.listInteractions({
      limit: 100,
      action: this.filterAction || undefined,
      offerCode: this.filterOfferCode || undefined,
    }).subscribe({
      next: (r: any) => { this.interactions = r?.data ?? []; this.loadingInter = false; },
      error: () => { this.interactions = []; this.loadingInter = false; }
    });
  }

  loadRecos(): void {
    this.loadingRecos = true;
    this.iaAdmin.listClientRecommendations({
      limit: 100,
      status: this.filterStatus || undefined,
      offerCode: this.filterOfferCode || undefined,
    }).subscribe({
      next: (r: any) => { this.recos = r?.data ?? []; this.loadingRecos = false; },
      error: () => { this.recos = []; this.loadingRecos = false; }
    });
  }

  loadAoa(): void {
    this.loadingAoa = true;
    this.iaAdmin.listActiveApplications(100).subscribe({
      next: (r: any) => { this.aoa = r?.data ?? []; this.loadingAoa = false; },
      error: () => { this.aoa = []; this.loadingAoa = false; }
    });
  }

  /** Stats FastAPI : juste pour le buffer (le reste vient de Spring) */
  loadFastApiStats(): void {
    this.iaAdmin.getMarketingFeedbackStats().subscribe({
      next: (r: any) => {
        const data = r?.data ?? r ?? {};
        this.bufferSizeFastApi = data.buffer_size ?? 0;
      },
      error: () => { this.bufferSizeFastApi = 0; }
    });
  }

  /* ═══════════════════════════════════════════════════════════
     ACTIONS ADMIN — Marketing Feedback
     ═══════════════════════════════════════════════════════════ */
  pushFeedback(): void {
    this.pushingFeedback = true;
    this.clearNotification();
    this.iaAdmin.pushMarketingFeedback().subscribe({
      next: (r: any) => {
        this.lastPushResult = {
          items_sent: r.items_sent ?? 0,
          last_cursor: r.last_cursor ?? '—',
        };
        this.showNotification(`${this.lastPushResult.items_sent} interaction(s) poussée(s) vers FastAPI`, 'success');
        this.pushingFeedback = false;
        setTimeout(() => { this.loadDashboard(); this.loadFastApiStats(); }, 500);
      },
      error: (e) => {
        this.showNotification(`Push échoué : ${e?.message || e}`, 'error');
        this.pushingFeedback = false;
      }
    });
  }

  retrain(): void {
    if (!confirm('Lancer le retrain du modèle marketing ?\n\nLes boosts des offres seront re-pondérés selon les taux\nd\'acceptation observés. Action non destructive.')) return;
    this.retraining = true;
    this.clearNotification();
    this.iaAdmin.retrainMarketingModel().subscribe({
      next: (r: any) => {
        this.lastRetrainResult = r?.data ?? r;
        const updated = this.lastRetrainResult?.offers_updated ?? 0;
        const msg = updated > 0
          ? `Retrain terminé : ${updated} offre(s) re-pondérée(s)`
          : `Retrain exécuté : ${this.lastRetrainResult?.note || 'aucune offre mise à jour'}`;
        this.showNotification(msg, 'success');
        this.retraining = false;
        setTimeout(() => { this.loadOffersStats(); }, 500);
      },
      error: (e) => {
        this.showNotification(`Retrain échoué : ${e?.message || e}`, 'error');
        this.retraining = false;
      }
    });
  }

  resetCursor(): void {
    const days = prompt('Renvoyer toutes les interactions depuis combien de jours ?\n(défaut: 7)', '7');
    if (days === null) return;
    const n = Math.max(1, parseInt(days, 10) || 7);
    this.resetting = true;
    this.iaAdmin.resetMarketingCursor(n).subscribe({
      next: (r: any) => {
        this.showNotification(`Curseur réinitialisé à J-${n} (${r?.new_cursor})`, 'success');
        this.resetting = false;
        setTimeout(() => this.loadDashboard(), 300);
      },
      error: (e) => {
        this.showNotification(`Reset échoué : ${e?.message || e}`, 'error');
        this.resetting = false;
      }
    });
  }

  /* ═══════════════════════════════════════════════════════════
     FILTRES — Marketing Feedback
     ═══════════════════════════════════════════════════════════ */
  applyFilters(): void {
    if (this.activeTab === 'interactions') this.loadInteractions();
    if (this.activeTab === 'recos') this.loadRecos();
  }

  clearFilters(): void {
    this.filterStatus = '';
    this.filterAction = '';
    this.filterOfferCode = '';
    this.applyFilters();
  }

  /* ═══════════════════════════════════════════════════════════
     AUTO-REFRESH — Marketing Feedback
     ═══════════════════════════════════════════════════════════ */
  toggleAutoRefresh(): void {
    this.autoRefresh = !this.autoRefresh;
    if (this.autoRefresh) {
      this.autoRefreshSub = interval(10000).subscribe(() => this.loadMarketingAll());
    } else {
      this.autoRefreshSub?.unsubscribe();
    }
  }

  /* ═══════════════════════════════════════════════════════════
     HELPERS UI — Marketing Feedback
     ═══════════════════════════════════════════════════════════ */
  ratePct(r: number | null | undefined): string {
    if (r === null || r === undefined) return '—';
    return Math.round(r * 100) + ' %';
  }

  rateColor(r: number | null | undefined): string {
    if (r === null || r === undefined) return '#94a3b8';
    if (r >= 0.6) return '#2e7d32';
    if (r >= 0.3) return '#e65100';
    return '#c62828';
  }

  statusColor(s: string): string {
    switch ((s || '').toUpperCase()) {
      case 'SENT': return '#1565c0';
      case 'OPENED': return '#6a1b9a';
      case 'ACCEPTED': return '#2e7d32';
      case 'REJECTED': return '#c62828';
      case 'PENDING': return '#94a3b8';
      default: return '#6b7fa3';
    }
  }

  actionColor(a: string): string {
    switch ((a || '').toLowerCase()) {
      case 'viewed': return '#94a3b8';
      case 'clicked': return '#1565c0';
      case 'accepted': return '#2e7d32';
      case 'rejected': return '#c62828';
      default: return '#6b7fa3';
    }
  }

  effectColor(e: string): string {
    switch ((e || '').toUpperCase()) {
      case 'FEE_WAIVER': return '#6a1b9a';
      case 'CASHBACK_RATE': return '#2e7d32';
      case 'DISCOUNT_PCT': return '#1565c0';
      default: return '#6b7fa3';
    }
  }

  short(id: string | number | null | undefined, n = 8): string {
    if (id === null || id === undefined) return '—';
    const s = String(id);
    return s.length > n ? s.substring(0, n) + '…' : s;
  }

  fmtDate(s: string | null | undefined): string {
    if (!s) return '—';
    try {
      const d = new Date(s);
      return d.toLocaleString('fr-FR', {
        year: '2-digit', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit',
      });
    } catch { return s; }
  }

  acceptBarPct(row: { accepted: number; total: number }): number {
    return row.total > 0 ? Math.round((row.accepted / row.total) * 100) : 0;
  }

  rejectBarPct(row: { rejected: number; total: number }): number {
    return row.total > 0 ? Math.round((row.rejected / row.total) * 100) : 0;
  }

  openedBarPct(row: { opened: number; total: number }): number {
    return row.total > 0 ? Math.round((row.opened / row.total) * 100) : 0;
  }
}