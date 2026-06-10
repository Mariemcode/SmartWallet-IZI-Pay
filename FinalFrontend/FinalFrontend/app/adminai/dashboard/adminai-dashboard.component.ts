import { Component, OnInit, OnDestroy } from '@angular/core';
import { interval, Subscription, catchError, of } from 'rxjs';
import { AdminAiService } from '../../services/adminai/adminai.service';
import {
  MlHealthStatus, MlMetrics, RetrainStatus, RecoMeta, getThreshold
} from '../../models/adminai.models';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

type AiPage =
  | 'overview'
  | 'predictions'
  | 'notifications'
  | 'rewards'
  | 'retrain'
  | 'alerts';

@Component({
  selector: 'app-adminai-dashboard',
  templateUrl: './adminai-dashboard.component.html',
  styleUrls: ['./adminai-dashboard.component.css'],
})
export class AdminAiDashboardComponent implements OnInit, OnDestroy {
  page: AiPage = 'overview';

  nav: { id: AiPage; label: string; icon: string }[] = [
    { id: 'overview',      label: 'Vue IA',           icon: 'bi-cpu' },
    { id: 'predictions',   label: 'Prédictions',      icon: 'bi-graph-up-arrow' },
    { id: 'notifications', label: 'Notifications',    icon: 'bi-bell' },
    { id: 'rewards',       label: 'Récompenses',      icon: 'bi-gift' },
    { id: 'retrain',       label: 'Réentraînement',   icon: 'bi-arrow-repeat' },
    { id: 'alerts',        label: 'Alertes IA',       icon: 'bi-exclamation-triangle' },
  ];

  // Data
  health:    MlHealthStatus | null = null;
  metrics:   MlMetrics      | null = null;
  retrainSt: RetrainStatus  | null = null;
  recoMeta:  RecoMeta       | null = null;
  stats:     any = null;
  monitoring: any = null;

  // Loading flags
  loadH = true;
  loadM = true;
  loadR = true;
  now   = new Date();
  readonly BILLS = ['TOPNET', 'BEE', 'SONEDE', 'STEG', 'TT', 'OOREDOO'];

  // Notifications
  nfCid          = '';
  nfTitre        = '';
  nfMsg          = '';
  nfType         = 'ATTENTION';
  nfAll          = false;
  nfResult       = '';
  nfSending      = false;
  nfScheduleDate = '';
  nfHistory:     any[] = [];
  nfHistPage     = 0;
  nfHistTotal    = 0;

  // Rewards
  rwList:      any[] = [];
  rwTitre      = '';
  rwDesc       = '';
  rwType       = 'CASHBACK';
  rwVal        = 0;
  rwPts        = 100;
  rwResult     = '';
  rwSelId      = '';
  rwClientIds  = '';
  rwAttrResult = '';

  // Alerts
  alertsList:    any[] = [];
  alertsPage     = 0;
  alertsTotal    = 0;
  alertsLoading  = false;
  filterSeverity = '';

  // Retrain
  retraining   = false;
  retrainMsg   = '';
  polling      = false;

  // Retrain history
  retrainHistory:        any[]    = [];
  retrainHistoryLoading  = false;
  retrainHistoryTotal    = 0;
  showRetrainHistory     = false;

  private performanceChart: Chart | null = null;
  private subs = new Subscription();

  constructor(private svc: AdminAiService) {}

  ngOnInit(): void {
    this.loadAll();
    this.subs.add(interval(30_000).subscribe(() => this.loadAll()));
    this.subs.add(interval(1_000).subscribe(() => (this.now = new Date())));
  }

  ngOnDestroy(): void {
    this.performanceChart?.destroy();
    this.subs.unsubscribe();
  }

  // ── Chargement global ──────────────────────────────────────
  loadAll(): void {
    this.svc.health().pipe(catchError(() => of(null))).subscribe(h => {
      this.health = h;
      this.loadH  = false;
    });
    this.svc.metrics().pipe(catchError(() => of(null))).subscribe(m => {
      this.metrics = m;
      this.loadM   = false;
    });
    this.svc.retrainStatus().pipe(catchError(() => of(null))).subscribe(s => {
      if (s) this.retrainSt = s;
    });
    this.svc.recoMeta().pipe(catchError(() => of(null))).subscribe(r => {
      this.recoMeta = r;
      this.loadR    = false;
    });
    this.svc.stats().pipe(catchError(() => of(null))).subscribe(s => {
      if (s) this.stats = s;
    });
    this.svc.monitoring().pipe(catchError(() => of(null))).subscribe(m => {
      if (m) this.monitoring = m;
    });
  }

  // ── Navigation ──────────────────────────────────────────────
  go(p: AiPage): void {
    this.page = p;
    if (p === 'notifications') this.loadNotifHist();
    if (p === 'rewards')       this.loadRewards();
    if (p === 'alerts')        this.loadAlerts();
    if (p === 'retrain')       this.loadRetrainHistory();
    if (p === 'predictions') {
      setTimeout(() => this.loadPerformanceHistory(), 100);
    }
  }

  // ── Notifications ──────────────────────────────────────────
  sendNotif(): void {
    this.nfSending = true;
    this.nfResult  = '';
    this.svc.sendNotif({
      client_id: this.nfAll ? 'ALL' : this.nfCid,
      titre:   this.nfTitre,
      message: this.nfMsg,
      type:    this.nfType,
    }).pipe(catchError(e => of({ error: e?.error?.error || 'Erreur' }))).subscribe((r: any) => {
      this.nfSending = false;
      this.nfResult  = r.error ? 'Erreur : ' + r.error : r.sent + ' notification(s) envoyée(s)';
      this.loadNotifHist();
    });
  }

  scheduleNotif(): void {
    this.svc.scheduleNotif({
      client_id:      this.nfAll ? 'ALL' : this.nfCid,
      titre:          this.nfTitre,
      message:        this.nfMsg,
      type:           this.nfType,
      date_planifiee: this.nfScheduleDate,
    }).pipe(catchError(() => of({ error: 'Erreur' }))).subscribe((r: any) => {
      this.nfResult = r.error ? 'Erreur' : 'Notification planifiée pour ' + this.nfScheduleDate;
      this.loadNotifHist();
    });
  }

  loadNotifHist(): void {
    this.svc.notifHistory(this.nfHistPage, 15)
      .pipe(catchError(() => of({ data: [], total: 0 })))
      .subscribe((r: any) => {
        this.nfHistory    = r.data  || [];
        this.nfHistTotal  = r.total || 0;
      });
  }

  // ── Récompenses ────────────────────────────────────────────
  loadRewards(): void {
    this.svc.rewards().pipe(catchError(() => of([]))).subscribe(r => (this.rwList = r));
  }

  createReward(): void {
    this.svc.createReward({
      titre:         this.rwTitre,
      description:   this.rwDesc,
      type:          this.rwType,
      valeur_tnd:    this.rwVal,
      points_requis: this.rwPts,
    }).pipe(catchError(() => of({ error: 'Erreur' }))).subscribe((r: any) => {
      this.rwResult = r.error ? 'Erreur' : 'Récompense créée';
      this.loadRewards();
      this.rwTitre = '';
      this.rwDesc  = '';
    });
  }

  attributeReward(): void {
    const ids = this.rwClientIds.split(',').map(s => s.trim()).filter(Boolean);
    this.svc.attributeReward({ reward_id: this.rwSelId, client_ids: ids })
      .pipe(catchError(() => of({ error: 'Erreur' }))).subscribe((r: any) => {
        this.rwAttrResult = r.error ? 'Erreur' : r.attributed + ' client(s) récompensé(s)';
      });
  }

  toggleReward(rw: any): void {
    if (!confirm(`Voulez-vous vraiment ${rw.actif ? 'désactiver' : 'activer'} "${rw.titre}" ?`)) return;
    this.svc.updateReward(rw.id, { actif: !rw.actif }).subscribe({
      next: () => { this.loadRewards(); this.rwResult = `Récompense ${rw.actif ? 'désactivée' : 'activée'}`; },
      error: err => { this.rwResult = `Erreur : ${err?.error?.message || 'Impossible'}`; },
    });
  }

  // ── Alertes ────────────────────────────────────────────────
  loadAlerts(): void {
    this.alertsLoading = true;
    this.svc.getAlerts(this.alertsPage, 20, this.filterSeverity).subscribe({
      next: (r: any) => {
        this.alertsList   = r.data  || [];
        this.alertsTotal  = r.total || 0;
        this.alertsLoading = false;
      },
      error: () => (this.alertsLoading = false),
    });
  }

  nextAlerts(): void { this.alertsPage++; this.loadAlerts(); }
  prevAlerts(): void { if (this.alertsPage > 0) { this.alertsPage--; this.loadAlerts(); } }
  filterAlerts(): void { this.alertsPage = 0; this.loadAlerts(); }

  // ── Réentraînement ─────────────────────────────────────────
  doRetrain(): void {
    this.retraining  = true;
    this.retrainMsg  = '';
    this.svc.retrain().pipe(catchError(() => {
      this.retrainMsg = 'Erreur lors du lancement';
      this.retraining = false;
      return of(null);
    })).subscribe(r => {
      if (r) {
        this.retrainMsg = r.message;
        this.retraining = false;
        this.pollRetrain();
      }
    });
  }

  pollRetrain(): void {
    if (this.polling) return;
    this.polling = true;
    let c = 0;
    const s = interval(5000).subscribe(() => {
      if (++c > 30) { s.unsubscribe(); this.polling = false; return; }
      this.svc.retrainStatus().pipe(catchError(() => of(null))).subscribe(st => {
        if (!st) return;
        this.retrainSt = st;
        if (st.status === 'success' || st.status === 'failed') {
          s.unsubscribe();
          this.polling    = false;
          this.retrainMsg = st.status === 'success' ? 'Réentraînement terminé ✓' : 'Échec du réentraînement';
          this.loadAll();
        }
      });
    });
    this.subs.add(s);
  }

  loadRetrainHistory(): void {
    this.retrainHistoryLoading = true;
    this.svc.retrainHistory()
      .pipe(catchError(() => of({ history: [], total: 0 })))
      .subscribe((r: any) => {
        this.retrainHistory       = r.history || [];
        this.retrainHistoryTotal  = r.total   || 0;
        this.retrainHistoryLoading = false;
      });
  }

  toggleRetrainHistory(): void {
    this.showRetrainHistory = !this.showRetrainHistory;
    if (this.showRetrainHistory && this.retrainHistory.length === 0) {
      this.loadRetrainHistory();
    }
  }

  // ── Graphique performance ──────────────────────────────────
  loadPerformanceHistory(): void {
    this.svc.getPerformanceHistory(30).subscribe({
      next: (data) => {
        const canvas = document.getElementById('aiPerformanceChart') as HTMLCanvasElement;
        if (!canvas) return;
        this.performanceChart?.destroy();
        const ctx = canvas.getContext('2d');
        if (!ctx) return;
        this.performanceChart = new Chart(ctx, {
          type: 'line',
          data: {
            labels: data.dates || [],
            datasets: [
              {
                label: 'R² TOPNET',
                data: data.r2Topnet || [],
                borderColor: '#1565c0',
                backgroundColor: '#1565c020',
                tension: 0.3, yAxisID: 'y',
              },
              {
                label: 'R² STEG',
                data: data.r2Steg || [],
                borderColor: '#2e7d32',
                backgroundColor: '#2e7d3220',
                tension: 0.3, yAxisID: 'y',
              },
              {
                label: 'MAE moy. (TND)',
                data: data.maeAvg || [],
                borderColor: '#f59e0b',
                backgroundColor: '#f59e0b20',
                tension: 0.3, yAxisID: 'y1',
              },
            ],
          },
          options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { labels: { color: '#333' }, position: 'top' } },
            scales: {
              x:  { ticks: { color: '#666' } },
              y:  { min: 0, max: 1, ticks: { color: '#666' },
                    title: { display: true, text: 'R²', color: '#666' } },
              y1: { position: 'right', grid: { display: false },
                    ticks: { color: '#f59e0b' },
                    title: { display: true, text: 'MAE (TND)', color: '#f59e0b' } },
            },
          },
        });
      },
      error: (err) => console.error('Erreur chargement historique performances:', err),
    });
  }

  // ── Helpers ────────────────────────────────────────────────
  getThreshold(l: string): number { return getThreshold(l); }

  r2Color(v: number): string {
    return v >= 0.9 ? '#2e7d32' : v >= 0.6 ? '#f59e0b' : '#c62828';
  }

  fmt(v: any): string {
    if (v == null) return '—';
    return Number(v).toLocaleString('fr-FR');
  }

  fmtTnd(v: any): string {
    if (v == null) return '—';
    return Number(v).toFixed(2) + ' TND';
  }

  fmtDate(iso: string | null): string {
    if (!iso) return '—';
    try {
      return new Date(iso.replace(' ', 'T')).toLocaleString('fr-FR', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit',
      });
    } catch { return iso ?? '—'; }
  }

  modulesLoaded(h: MlHealthStatus | null): string {
    if (!h) return '—';
    const count = [h.modules.m1_factures, h.modules.m2_recharge,
                   h.modules.m3_budget, h.modules.m4_habitudes]
                  .filter(Boolean).length;
    return `${count} / 4`;
  }

  entries(o: any): { k: string; v: any }[] {
    return Object.entries(o || {}).map(([k, v]) => ({ k, v }));
  }

  statusClass(s: string): string {
    switch ((s || '').toLowerCase()) {
      case 'up':      return 'badge-success';
      case 'degraded':return 'badge-warning';
      case 'down':    return 'badge-danger';
      case 'success': return 'badge-success';
      case 'failed':  return 'badge-danger';
      case 'running': return 'badge-info';
      default:        return 'badge-secondary';
    }
  }
}
