import { Component, OnInit, OnDestroy, AfterViewInit } from '@angular/core';
import { interval, Subscription, catchError, of, forkJoin } from 'rxjs';
import { Chart, registerables } from 'chart.js';
import { IaAdminService } from '../../services/ia-admin/ia-admin.service';
import {
    MlHealthStatus, MlMetrics, RetrainStatus, RecoMeta, getThreshold,
} from '../../models/ia-admin.models';

Chart.register(...registerables);

@Component({
    selector: 'app-retrain-ml',
    templateUrl: './retrain-ml.component.html',
    styleUrl: './retrain-ml.component.css',
})
export class RetrainMlComponent implements OnInit, OnDestroy, AfterViewInit {

    // ── Source de vérité backend ──
    health:     MlHealthStatus | null = null;
    metrics:    MlMetrics       | null = null;
    retrainSt:  RetrainStatus   | null = null;
    recoMeta:   RecoMeta        | null = null;
    monitoring: any | null = null;       // ★ PKL vs Live
    liveMon:    any | null = null;        // ★ Monitoring Live recalculé (MAE/RMSE/ratio/statut + par fournisseur)
    checking = false;                     // état du bouton « Vérifier maintenant »
    runningJob: string | null = null;     // id du job scheduler en cours d'exécution
    ocrStats:   any | null = null;       // ★ OCR legacy
    ocrStatsV2: any | null = null;       // ★ v7 — Spring direct
    schedulers: any[] = [];              // ★ jobs cron
    recoStats:  any | null = null;       // ★ Module 6 stats

    // ── Audit (PKL vs Live) — v7 ──
    auditStatusData: any | null = null;
    evaluating = false;
    evaluateMsg = '';

    // ── OCR listes — v7 ──
    scannedFactures: any[] = [];
    ocrFeedbacks: any[] = [];
    loadScans = false;
    loadFeedbacks = false;

    // ── Retrain ──
    retraining = false;
    retrainMsg = '';
    polling = false;
    pollingSub: Subscription | null = null;

    // ── OCR ──
    ocrAnalyzing = false;
    ocrAnalysisMsg = '';

    // ── History ──
    retrainHistory: any[] = [];
    retrainHistoryLoading = false;
    showRetrainHistory = false;

    // ── Loaders ──
    loadH = true;
    loadM = true;
    loadR = true;
    loadMon = true;
    loadOcr = true;
    loadSched = true;
    loadRecoStats = true;
    now = new Date();

    // ── Onglets ──
    activeTab: 'overview' | 'pkl_vs_live' | 'history' | 'peer' | 'ocr' | 'schedulers' = 'overview';

    readonly BILLS = ['TOPNET', 'BEE', 'SONEDE', 'STEG', 'TT', 'OOREDOO'];
    private subs = new Subscription();
    private performanceChart: Chart | null = null;
    private maeComparisonChart: Chart | null = null;
    private clusterChart: Chart | null = null;

    constructor(private svc: IaAdminService) {}

    ngOnInit(): void {
        this.loadAll();
        // Refresh auto toutes les 30s
        this.subs.add(interval(30000).subscribe(() => this.loadAll()));
        this.subs.add(interval(1000).subscribe(() => (this.now = new Date())));
    }

    ngAfterViewInit(): void {
        setTimeout(() => {
            this.loadPerformanceHistory();
        }, 300);
    }

    ngOnDestroy(): void {
        this.performanceChart?.destroy();
        this.maeComparisonChart?.destroy();
        this.clusterChart?.destroy();
        this.subs.unsubscribe();
        this.pollingSub?.unsubscribe();
    }

    setTab(t: 'overview' | 'pkl_vs_live' | 'history' | 'peer' | 'ocr' | 'schedulers') {
        this.activeTab = t;
        setTimeout(() => {
            if (t === 'pkl_vs_live') this.renderMaeComparisonChart();
            if (t === 'peer')        this.renderClusterChart();
            if (t === 'overview')    this.loadPerformanceHistory();
            if (t === 'ocr') {
                // ★ v7 — Charger les listes OCR à la demande
                if (this.scannedFactures.length === 0) this.loadScannedFactures();
                if (this.ocrFeedbacks.length === 0)    this.loadOcrFeedbacks();
            }
        }, 100);
    }

    // ═══════════════════════════════════════════════════════════
    //  Chargement global
    // ═══════════════════════════════════════════════════════════
    loadAll(): void {
        this.svc.health().pipe(catchError(() => of(null))).subscribe(h => {
            this.health = h; this.loadH = false;
        });
        this.svc.metrics().pipe(catchError(() => of(null))).subscribe(m => {
            this.metrics = m; this.loadM = false;
        });
        this.svc.retrainStatus().pipe(catchError(() => of(null))).subscribe(s => {
            if (s) this.retrainSt = s;
        });
        this.svc.recoMeta().pipe(catchError(() => of(null))).subscribe(r => {
            this.recoMeta = r; this.loadR = false;
        });
        this.svc.monitoringLive().pipe(catchError(() => of(null))).subscribe(l => { this.liveMon = l; });
        this.svc.monitoring().pipe(catchError(() => of(null))).subscribe(m => {
            this.monitoring = m; this.loadMon = false;            if (this.activeTab === 'pkl_vs_live') setTimeout(() => this.renderMaeComparisonChart(), 50);
        });
        this.svc.ocrStats().pipe(catchError(() => of(null))).subscribe(o => {
            this.ocrStats = o; this.loadOcr = false;
        });
        this.svc.schedulers().pipe(catchError(() => of({ schedulers: [] }))).subscribe((s: any) => {
            this.schedulers = s?.schedulers ?? []; this.loadSched = false;
        });
        this.svc.recommendationsStats().pipe(catchError(() => of(null))).subscribe(r => {
            this.recoStats = r; this.loadRecoStats = false;
            if (this.activeTab === 'peer') setTimeout(() => this.renderClusterChart(), 50);
        });
        // ★ v7 — Audit status (PKL vs Live)
        this.svc.auditStatus().pipe(catchError(() => of(null))).subscribe(a => {
            this.auditStatusData = a;
        });
        // ★ v7 — OCR Spring direct (toujours à jour)
        this.svc.ocrStatsV2().pipe(catchError(() => of(null))).subscribe(o => {
            this.ocrStatsV2 = o;
        });
    }

    /** ★ v7 — Charge la liste des factures scannées (onglet OCR) */
    loadScannedFactures(): void {
        this.loadScans = true;
        this.svc.listScannedFactures(50).pipe(catchError(() => of({ data: [] })))
            .subscribe((r: any) => {
                this.scannedFactures = r?.data ?? [];
                this.loadScans = false;
            });
    }

    /** ★ v7 — Charge la liste des feedbacks OCR (onglet OCR) */
    loadOcrFeedbacks(): void {
        this.loadFeedbacks = true;
        this.svc.listOcrFeedbacks(50).pipe(catchError(() => of({ data: [] })))
            .subscribe((r: any) => {
                this.ocrFeedbacks = r?.data ?? [];
                this.loadFeedbacks = false;
            });
    }

    /** ★ v7 — Force l'évaluation du audit log (calcule MAE Live immédiatement) */
    doEvaluateAudit(): void {
        if (!confirm('Lancer l\'évaluation des prédictions passées maintenant ?\n\n' +
            'Cela compare chaque prédiction snapshot à la vraie transaction payée\n' +
            'et calcule la MAE Live par fournisseur.')) return;
        this.evaluating = true;
        this.evaluateMsg = '';
        this.svc.evaluateAuditNow().pipe(catchError(e => {
            this.evaluateMsg = '❌ Erreur : ' + (e?.error?.error || 'réseau');
            this.evaluating = false;
            return of(null);
        })).subscribe((r: any) => {
            if (r) {
                this.evaluateMsg = r.message || `${r.logs_evalues || 0} logs évalués`;
                this.evaluating = false;
                // Recharger monitoring + audit status pour voir Live MAE recalculée
                setTimeout(() => {
                    this.svc.monitoring().pipe(catchError(() => of(null))).subscribe(m => {
                        this.monitoring = m;
                        if (this.activeTab === 'pkl_vs_live') this.renderMaeComparisonChart();
                    });
                    this.svc.auditStatus().pipe(catchError(() => of(null))).subscribe(a => this.auditStatusData = a);
                }, 500);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Retrain
    // ═══════════════════════════════════════════════════════════
    doRetrain(): void {
        if (!confirm('Lancer le réentraînement des modèles forecasting ?\n\nCela peut prendre plusieurs minutes.')) return;
        this.retraining = true;
        this.retrainMsg = '';
        this.svc.retrain().pipe(catchError(e => {
            this.retrainMsg = 'Erreur : ' + (e?.error?.error || 'réseau');
            this.retraining = false;
            return of(null);
        })).subscribe((r: any) => {
            if (r) {
                this.retrainMsg = r.message || 'Réentraînement lancé';
                this.retraining = false;
                this.pollRetrain();
            }
        });
    }

    pollRetrain(): void {
        if (this.polling) return;
        this.polling = true;
        let c = 0;
        this.pollingSub = interval(5000).subscribe(() => {
            if (++c > 60) {
                this.pollingSub?.unsubscribe();
                this.polling = false;
                return;
            }
            this.svc.retrainStatus().pipe(catchError(() => of(null))).subscribe(st => {
                if (!st) return;
                this.retrainSt = st;
                if (st.status === 'success' || st.status === 'failed') {
                    this.pollingSub?.unsubscribe();
                    this.polling = false;
                    this.retrainMsg = st.status === 'success'
                        ? 'Réentraînement terminé avec succès'
                        : 'Échec du réentraînement';
                    this.loadAll();
                }
            });
        });
    }

    loadRetrainHistory(): void {
        this.retrainHistoryLoading = true;
        this.svc.retrainHistory()
            .pipe(catchError(() => of({ history: [] })))
            .subscribe((r: any) => {
                this.retrainHistory = r.history || [];
                this.retrainHistoryLoading = false;
            });
    }

    toggleRetrainHistory(): void {
        this.showRetrainHistory = !this.showRetrainHistory;
        if (this.showRetrainHistory && this.retrainHistory.length === 0) {
            this.loadRetrainHistory();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  OCR analyse manuelle
    // ═══════════════════════════════════════════════════════════
    doOcrAnalysis(): void {
        if (!confirm('Déclencher l\'analyse des feedbacks OCR maintenant ?\n\n(Sans attendre le cron du dimanche à 3h)')) return;
        this.ocrAnalyzing = true;
        this.ocrAnalysisMsg = '';
        this.svc.triggerOcrAnalysis().pipe(catchError(e => {
            this.ocrAnalysisMsg = 'Erreur : ' + (e?.error?.error || 'réseau');
            this.ocrAnalyzing = false;
            return of(null);
        })).subscribe((r: any) => {
            if (r) {
                this.ocrAnalysisMsg = r.message || 'Analyse OCR lancée';
                this.ocrAnalyzing = false;
                // Recharger les stats OCR après 2s
                setTimeout(() => this.svc.ocrStats().pipe(catchError(() => of(null)))
                    .subscribe(o => this.ocrStats = o), 2000);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Charts
    // ═══════════════════════════════════════════════════════════
    loadPerformanceHistory(): void {
        this.svc.performanceHistory(30).pipe(catchError(() => of(null))).subscribe(data => {
            if (!data) return;
            const canvas = document.getElementById('performanceChart') as HTMLCanvasElement | null;
            if (!canvas) return;
            this.performanceChart?.destroy();
            const ctx = canvas.getContext('2d');
            if (!ctx) return;
            this.performanceChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: data.dates || [],
                    datasets: [
                        { label: 'R² TOPNET', data: data.r2Topnet || [],
                            borderColor: '#6366f1', backgroundColor: '#6366f133',
                            tension: 0.3, yAxisID: 'y' },
                        { label: 'R² STEG', data: data.r2Steg || [],
                            borderColor: '#10b981', backgroundColor: '#10b98133',
                            tension: 0.3, yAxisID: 'y' },
                        { label: 'MAE moy. (TND)', data: data.maeAvg || [],
                            borderColor: '#f59e0b', backgroundColor: '#f59e0b33',
                            tension: 0.3, yAxisID: 'y1' },
                    ],
                },
                options: {
                    responsive: true, maintainAspectRatio: false,
                    plugins: { legend: { position: 'top' } },
                    scales: {
                        y:  { min: 0, max: 1, title: { display: true, text: 'R²' } },
                        y1: { position: 'right', grid: { display: false },
                            title: { display: true, text: 'MAE (TND)' } },
                    },
                },
            });
        });
    }

    /** Comparaison MAE PKL (offline, depuis /metrics) vs MAE Live (depuis /monitoring) */
    renderMaeComparisonChart(): void {
        const canvas = document.getElementById('maeComparisonChart') as HTMLCanvasElement | null;
        if (!canvas) return;
        this.maeComparisonChart?.destroy();
        const ctx = canvas.getContext('2d');
        if (!ctx || !this.metrics) return;

        const pkl: number[] = [];
        const live: number[] = [];
        const maeParFournisseur = this.monitoring?.mae_par_fournisseur ?? {};

        for (const label of this.BILLS) {
            const pklVal = (this.metrics.module1_factures?.[label] as any)?.mae ?? 0;
            pkl.push(pklVal);
            const liveData = maeParFournisseur[label];
            const liveVal = liveData?.mae_TND;
            live.push(typeof liveVal === 'number' ? liveVal : 0);
        }

        this.maeComparisonChart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: this.BILLS,
                datasets: [
                    { label: 'MAE PKL (offline, à l\'entraînement)', data: pkl,
                        backgroundColor: '#6366f1', borderRadius: 6 },
                    { label: 'MAE Live (réel, 90 derniers jours)', data: live,
                        backgroundColor: '#f59e0b', borderRadius: 6 },
                ],
            },
            options: {
                responsive: true, maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'top' },
                    tooltip: { callbacks: { label: (c) => `${c.dataset.label}: ${c.parsed.y!.toFixed(2)} TND` } }
                },
                scales: {
                    y: { title: { display: true, text: 'MAE (TND, plus petit = meilleur)' }, beginAtZero: true }
                }
            }
        });
    }

    /** Camembert répartition clients par cluster (Module 6) */
    renderClusterChart(): void {
        const canvas = document.getElementById('clusterChart') as HTMLCanvasElement | null;
        if (!canvas) return;
        this.clusterChart?.destroy();
        const ctx = canvas.getContext('2d');
        // FastAPI /recommendations/stats renvoie distribution_profiles : { id: { nb_clients, nom } }
        const rep = this.recoStats?.distribution_profiles as Record<string, { nb_clients: number; nom: string }> | undefined;
        if (!ctx || !rep || !Object.keys(rep).length) return;

        const entries = Object.entries(rep);
        const labels = entries.map(([id, v]) => v?.nom ?? `Cluster ${id}`);
        const data = entries.map(([, v]) => v?.nb_clients ?? 0);

        this.clusterChart = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: labels,
                datasets: [{
                    data,
                    backgroundColor: ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#a855f7', '#06b6d4', '#84cc16'],
                    borderWidth: 0,
                }],
            },
            options: {
                responsive: true, maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'right' },
                    tooltip: {
                        callbacks: {
                            label: (c) => `${c.label}: ${c.parsed} clients (${((c.parsed / data.reduce((a, b) => +a + +b, 0)) * 100).toFixed(1)}%)`
                        }
                    }
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════
    getThreshold(l: string): number { return getThreshold(l); }

    r2Color(v: number): string {
        return v >= 0.9 ? '#10b981' : v >= 0.6 ? '#f59e0b' : '#dc2626';
    }

    /** Compare MAE Live à MAE PKL, retourne ratio + couleur de dégradation */
    /** Tous les jobs sont déclenchables manuellement (backend /schedulers/run/{id}). */
    isRunnable(_id: string): boolean { return true; }

    /** Exécute un job scheduler à la demande (sans attendre le cron). */
    runJob(id: string): void {
        this.runningJob = id;
        this.svc.runScheduler(id).pipe(catchError(() => of(null))).subscribe((r: any) => {
            this.runningJob = null;
            alert(r?.message ?? 'Job exécuté.');
            // si on vient de relancer le monitoring, on rafraîchit les valeurs Live
            if (id === 'monitor_performance') {
                this.svc.monitoringLive().pipe(catchError(() => of(null))).subscribe(l => { this.liveMon = l; });
            }
        });
    }

    /** Détail Live d'un fournisseur (depuis /monitoring/live, recalculé en SQL). */
    liveFour(label: string): any | null {
        return this.liveMon?.par_fournisseur?.[label] ?? null;
    }

    /** Vérifie la dégradation à la demande (démo jury) : recharge le Live + crée une notif si dégradé. */
    checkNow(autoRetrain = false): void {
        this.checking = true;
        this.svc.checkDegradationNow(autoRetrain).pipe(catchError(() => of(null))).subscribe((r: any) => {
            this.checking = false;
            this.liveMon = r ? { ...this.liveMon, ...r } : this.liveMon;
            if (r?.alerte_creee) {
                alert('⚠️ Dégradation détectée — notification admin créée.' +
                    (r?.retrain_lance ? ' Réentraînement déclenché.' : ''));
            } else {
                alert('✅ Modèle stable (statut : ' + (r?.statut ?? '—') + ').');
            }
        });
    }

    /** Comparaison MAE PKL (offline) vs MAE Live — désormais basée sur /monitoring/live. */
    degradationRatio(label: string): { ratio: number; color: string; level: string } | null {
        const f = this.liveFour(label);
        const ratio = f?.ratio;
        if (typeof ratio !== 'number') return null;
        let color = '#10b981', level = 'Stable';
        if (ratio >= 1.5) { color = '#dc2626'; level = 'Dégradation forte'; }
        else if (ratio >= 1.2) { color = '#f59e0b'; level = 'Dégradation modérée'; }
        return { ratio, color, level };
    }

    fmtTnd(v: any): string {
        if (v == null || v === 'N/A') return '—';
        const n = Number(v);
        if (isNaN(n)) return String(v);
        return n.toFixed(2) + ' TND';
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

    categoryColor(cat: string): string {
        switch ((cat || '').toLowerCase()) {
            case 'forecasting ml':  return '#6366f1';
            case 'notifications':   return '#10b981';
            case 'ocr':             return '#f59e0b';
            case 'marketing ia':    return '#a855f7';
            case 'surveillance':    return '#06b6d4';
            case 'maintenance':     return '#64748b';
            default:                return '#94a3b8';
        }
    }

    /** Détecte si le scheduler `monitor_performance` a flagué une dégradation
     *  (lecture des degradation_alerts du metrics) */
    get hasDegradationAlerts(): boolean {
        return (this.metrics?.degradation_alerts?.length ?? 0) > 0;
    }

    protected readonly Math = Math;
}