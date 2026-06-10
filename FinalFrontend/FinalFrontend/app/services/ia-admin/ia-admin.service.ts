/**
 * IA Admin Service — accès aux endpoints `/api/ia/admin/*` et `/api/marketing/*`
 * exposés par Spring Boot client-dashboard (port 8090, via Gateway 8222).
 *
 * Couvre :
 *   • Système & santé globale ML (forecasting modules 1-5)
 *   • Retrain forecasting (≠ retrain classification)
 *   • Clients enrichis (avec prédictions ML — view admin)
 *   • Notifications push (envoi + planification + historique)
 *   • Marketing : dashboard, recos, interactions, stats, push/retrain
 *   • Alertes IA + monitoring (PKL vs Live)
 *   • Schedulers + OCR feedback
 *   • Reco meta + stats (Module 6 — peer-comparison)
 */
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
    MlHealthStatus, MlMetrics, RetrainStatus, RecoMeta, enrichMetrics,
} from '../../models/ia-admin.models';

@Injectable({ providedIn: 'root' })
export class IaAdminService {
    /** Préfixe IA Admin → `/api/ia/admin/*` */
    private readonly base = `${environment.api.ia}/admin`;

    /** Préfixe marketing dashboard → `/api/marketing/*` */
    private readonly mktBase = environment.api.marketing;

    constructor(private http: HttpClient) {}

    // ════════════════════════════════════════════════════════════════
    //  SYSTÈME & SANTÉ
    // ════════════════════════════════════════════════════════════════
    health(): Observable<MlHealthStatus> {
        return this.http.get<MlHealthStatus>(`${this.base}/health`);
    }

    metrics(): Observable<MlMetrics> {
        return this.http.get<MlMetrics>(`${this.base}/metrics`).pipe(
            map(m => {
                const bm: Record<string, any> = {};
                for (const [l, r] of Object.entries(m.module1_factures ?? {})) {
                    bm[l] = enrichMetrics(l, r as any);
                }
                return { ...m, billMetrics: bm };
            }),
        );
    }

    /** Stats Spring (DB locale) — total_clients, total_tx, tx_ce_mois, etc. */
    stats(): Observable<any> { return this.http.get(`${this.base}/stats`); }

    /**
     * MONITORING — PKL (offline) vs LIVE (sur 30j en base réelle)
     * Renvoie : taux_confirmation_30j, mae_en_ligne_30j_TND,
     *           mae_par_fournisseur, repartition_statuts_30j, metrics_ml
     */
    monitoring(): Observable<any> { return this.http.get(`${this.base}/monitoring`); }

    /** Monitoring Live recalculé en direct (MAE/RMSE Live vs PKL + statut + détail par fournisseur). */
    monitoringLive(): Observable<any> { return this.http.get(`${this.base}/monitoring/live`); }
    /** Vérifie la dégradation à la demande ; crée une notif admin si dégradé. */
    checkDegradationNow(autoRetrain = false): Observable<any> {
        return this.http.post(`${this.base}/monitoring/check-now`, { auto_retrain: autoRetrain });
    }
    /** Exécute un job scheduler à la demande (sans attendre le cron). */
    runScheduler(id: string): Observable<any> {
        return this.http.post(`${this.base}/schedulers/run/${id}`, {});
    }

    // ════════════════════════════════════════════════════════════════
    //  RETRAIN FORECASTING
    // ════════════════════════════════════════════════════════════════
    retrain():        Observable<any>           { return this.http.post(`${this.base}/retrain`, {}); }
    retrainStatus():  Observable<RetrainStatus> { return this.http.get<RetrainStatus>(`${this.base}/retrain/status`); }
    retrainHistory(): Observable<any>           { return this.http.get(`${this.base}/retrain/history`); }

    /** Historique métriques sur N jours (chart 30j) */
    performanceHistory(days = 30): Observable<any> {
        return this.http.get(`${this.base}/metrics/history?days=${days}`);
    }

    // ════════════════════════════════════════════════════════════════
    //  RECOMMANDATIONS (Module 6 — peer-comparison)
    // ════════════════════════════════════════════════════════════════

    /** Métadonnées (nb clusters, nb recos chargées, silhouette, etc.) */
    recoMeta(): Observable<RecoMeta> {
        return this.http.get<RecoMeta>(`${this.base}/reco/meta`);
    }

    /**
     * Stats du Module 6 :
     *   { total_clients_profiles, total_recommendations, total_alerts,
     *     total_challenges, clients_avec_reco, clients_avec_alertes,
     *     repartition_clusters }
     */
    recommendationsStats(): Observable<any> {
        return this.http.get(`${this.base}/recommendations/stats`);
    }

    // ════════════════════════════════════════════════════════════════
    //  ALERTES (anomalies — Z-Score, Isolation Forest)
    // ════════════════════════════════════════════════════════════════
    getAlerts(page = 0, size = 20, severity?: string): Observable<any> {
        let url = `${this.base}/alerts?page=${page}&size=${size}`;
        if (severity) url += `&severity=${severity}`;
        return this.http.get(url);
    }

    // ════════════════════════════════════════════════════════════════
    //  OCR (Module 7 — scan factures + auto-apprentissage)
    // ════════════════════════════════════════════════════════════════

    /** Stats OCR : nb scans, taux confirmation, fournisseurs détectés, etc. */
    ocrStats(): Observable<any> {
        return this.http.get(`${this.base}/ocr/stats`);
    }

    /** ★ NOUVEAU v7 — Stats OCR calculées directement depuis Spring (toujours à jour) */
    ocrStatsV2(): Observable<any> {
        return this.http.get(`${this.base}/ocr/stats-v2`);
    }

    /** ★ NOUVEAU v7 — Liste des factures scannées (debug visuel) */
    listScannedFactures(limit = 50): Observable<any> {
        return this.http.get(`${this.base}/ocr/scanned-factures?limit=${limit}`);
    }

    /** ★ NOUVEAU v7 — Liste des feedbacks OCR utilisateurs */
    listOcrFeedbacks(limit = 50): Observable<any> {
        return this.http.get(`${this.base}/ocr/feedbacks?limit=${limit}`);
    }

    /** Déclenche manuellement l'analyse des feedbacks OCR (sans attendre dimanche 3h) */
    triggerOcrAnalysis(): Observable<any> {
        return this.http.post(`${this.base}/ocr/analyser-feedback`, {});
    }

    // ════════════════════════════════════════════════════════════════
    //  AUDIT — PKL vs Live (★ NOUVEAU v7)
    // ════════════════════════════════════════════════════════════════

    /** Force l'évaluation des prédictions passées (sans attendre 3h du mat) */
    evaluateAuditNow(): Observable<any> {
        return this.http.post(`${this.base}/audit/evaluate-now`, {});
    }

    /** Status du audit log : pending / done / by_fournisseur */
    auditStatus(): Observable<any> {
        return this.http.get(`${this.base}/audit/status`);
    }

    // ════════════════════════════════════════════════════════════════
    //  SCHEDULERS — liste des jobs cron
    // ════════════════════════════════════════════════════════════════
    schedulers(): Observable<any> {
        return this.http.get(`${this.base}/schedulers`);
    }

    // ════════════════════════════════════════════════════════════════
    //  CLIENTS (vue enrichie ML)
    // ════════════════════════════════════════════════════════════════
    clients(page = 0, size = 20, search?: string): Observable<any> {
        let url = `${this.base}/clients?page=${page}&size=${size}`;
        if (search) url += `&search=${encodeURIComponent(search)}`;
        return this.http.get(url);
    }
    clientDetail(id: string): Observable<any> {
        return this.http.get(`${this.base}/clients/${id}`);
    }

    // ════════════════════════════════════════════════════════════════
    //  NOTIFICATIONS PUSH
    // ════════════════════════════════════════════════════════════════
    sendNotif(body: any):     Observable<any> { return this.http.post(`${this.base}/notifications/send`, body); }
    scheduleNotif(body: any): Observable<any> { return this.http.post(`${this.base}/notifications/schedule`, body); }
    notifHistory(page = 0, size = 20): Observable<any> {
        return this.http.get(`${this.base}/notifications/history?page=${page}&size=${size}`);
    }

    // ════════════════════════════════════════════════════════════════
    //  MARKETING — actions admin (push batch / retrain FastAPI)
    // ════════════════════════════════════════════════════════════════
    pushMarketingFeedback(): Observable<any> {
        return this.http.post(`${this.base}/marketing-feedback/push`, {});
    }
    retrainMarketingModel(): Observable<any> {
        return this.http.post(`${this.base}/marketing-feedback/retrain`, {});
    }
    getMarketingFeedbackStats(): Observable<any> {
        return this.http.get(`${this.base}/marketing-feedback/stats`);
    }
    resetMarketingCursor(daysBack = 7): Observable<any> {
        return this.http.post(`${this.base}/marketing-feedback/reset-cursor`, { days_back: daysBack });
    }
    getMarketingCursor(): Observable<any> {
        return this.http.get(`${this.base}/marketing-feedback/cursor`);
    }

    // ════════════════════════════════════════════════════════════════
    //  MARKETING — données Spring (source de vérité)
    // ════════════════════════════════════════════════════════════════
    getMarketingDashboard(): Observable<any> { return this.http.get(`${this.mktBase}/dashboard`); }
    getOffersStats():        Observable<any> { return this.http.get(`${this.mktBase}/offers/stats`); }
    getProfilesStats():      Observable<any> { return this.http.get(`${this.mktBase}/profiles/stats`); }

    listClientRecommendations(opts: { limit?: number; status?: string; offerCode?: string } = {}): Observable<any> {
        let params = new HttpParams();
        if (opts.limit)     params = params.set('limit', String(opts.limit));
        if (opts.status)    params = params.set('status', opts.status);
        if (opts.offerCode) params = params.set('offerCode', opts.offerCode);
        return this.http.get(`${this.mktBase}/recommendations`, { params });
    }

    listInteractions(opts: { limit?: number; action?: string; offerCode?: string } = {}): Observable<any> {
        let params = new HttpParams();
        if (opts.limit)     params = params.set('limit', String(opts.limit));
        if (opts.action)    params = params.set('action', opts.action);
        if (opts.offerCode) params = params.set('offerCode', opts.offerCode);
        return this.http.get(`${this.mktBase}/interactions`, { params });
    }

    listActiveApplications(limit = 100): Observable<any> {
        return this.http.get(`${this.mktBase}/active-applications?limit=${limit}`);
    }
}