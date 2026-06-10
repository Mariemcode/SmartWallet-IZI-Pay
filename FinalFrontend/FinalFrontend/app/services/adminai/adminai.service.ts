import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  MlHealthStatus, MlMetrics, RetrainStatus, RecoMeta,
  enrichMetrics
} from '../../models/adminai.models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AdminAiService {
  // Tous les appels passent par la gateway 8222
  private ia  = environment.api.iaAdmin;  // /api/ia/admin
  private adm = environment.api;           // raccourci vers les autres préfixes

  constructor(private h: HttpClient) {}

  // ═══ SYSTÈME ML (client-dashboard → FastAPI) ═══════════════
  health(): Observable<MlHealthStatus> {
    return this.h.get<MlHealthStatus>(`${this.ia}/health`);
  }

  metrics(): Observable<MlMetrics> {
    return this.h.get<MlMetrics>(`${this.ia}/metrics`).pipe(
      map(m => {
        const bm: Record<string, any> = {};
        for (const [l, r] of Object.entries(m.module1_factures ?? {})) {
          bm[l] = enrichMetrics(l, r);
        }
        return { ...m, billMetrics: bm };
      })
    );
  }

  monitoring(): Observable<any> {
    return this.h.get(`${this.ia}/monitoring`);
  }

  stats(): Observable<any> {
    return this.h.get(`${this.ia}/stats`);
  }

  getPerformanceHistory(days = 30): Observable<any> {
    return this.h.get(`${this.ia}/metrics/history?days=${days}`);
  }

  // ═══ RÉENTRAÎNEMENT ════════════════════════════════════════
  retrain(): Observable<any> {
    return this.h.post(`${this.ia}/retrain`, {});
  }

  retrainStatus(): Observable<RetrainStatus> {
    return this.h.get<RetrainStatus>(`${this.ia}/retrain/status`);
  }

  retrainHistory(): Observable<any> {
    return this.h.get(`${this.ia}/retrain/history`);
  }

  // ═══ RECOMMANDATIONS META ══════════════════════════════════
  recoMeta(): Observable<RecoMeta> {
    return this.h.get<RecoMeta>(`${this.ia}/reco/meta`);
  }

  // ═══ CLIENTS IA ════════════════════════════════════════════
  clients(p = 0, s = 20, q?: string): Observable<any> {
    let u = `${this.ia}/clients?page=${p}&size=${s}`;
    if (q) u += `&search=${encodeURIComponent(q)}`;
    return this.h.get(u);
  }

  clientDetail(id: string): Observable<any> {
    return this.h.get(`${this.ia}/clients/${id}`);
  }

  // ═══ NOTIFICATIONS ═════════════════════════════════════════
  sendNotif(body: any): Observable<any> {
    return this.h.post(`${this.ia}/notifications/send`, body);
  }

  scheduleNotif(body: any): Observable<any> {
    return this.h.post(`${this.ia}/notifications/schedule`, body);
  }

  notifHistory(p = 0, s = 20): Observable<any> {
    return this.h.get(`${this.ia}/notifications/history?page=${p}&size=${s}`);
  }

  // ═══ RÉCOMPENSES ═══════════════════════════════════════════
  rewards(): Observable<any> {
    return this.h.get(`${this.ia}/rewards`);
  }

  createReward(body: any): Observable<any> {
    return this.h.post(`${this.ia}/rewards`, body);
  }

  updateReward(id: string, body: any): Observable<any> {
    return this.h.put(`${this.ia}/rewards/${id}`, body);
  }

  attributeReward(body: any): Observable<any> {
    return this.h.post(`${this.ia}/rewards/attribute`, body);
  }

  rewardClients(id: string): Observable<any> {
    return this.h.get(`${this.ia}/rewards/${id}/clients`);
  }

  // ═══ ALERTES IA ════════════════════════════════════════════
  getAlerts(page = 0, size = 20, severity?: string): Observable<any> {
    let url = `${this.ia}/alerts?page=${page}&size=${size}`;
    if (severity) url += `&severity=${severity}`;
    return this.h.get(url);
  }

  // ═══ CLASSIFICATION IA (binôme — via gateway) ══════════════
  getAiHealth(): Observable<any> {
    return this.h.get(`${this.adm.classification}/health`);
  }

  predictProfile(clientId: string): Observable<any> {
    return this.h.post(`${this.adm.classification}/predict`, { client_id: clientId });
  }

  getProfilesSummary(): Observable<any> {
    return this.h.get(`${this.adm.classification}/profiles/summary`);
  }

  getClientProfile(clientId: string): Observable<any> {
    return this.h.get(`${this.adm.classification}/clients/${clientId}`);
  }

  getChurnClients(threshold = 0.5, limit = 50): Observable<any> {
    return this.h.get(`${this.adm.classification}/clients/churn?threshold=${threshold}&limit=${limit}`);
  }

  getKpiSummary(): Observable<any> {
    return this.h.get(`${this.adm.classification}/kpi/summary`);
  }

  getDriftStatus(): Observable<any> {
    return this.h.get(`${this.adm.classification}/drift/status`);
  }

  retrainClassification(): Observable<any> {
    return this.h.post(`${this.adm.classification}/admin/retrain`, {});
  }

  getClassifRetrainStatus(): Observable<any> {
    return this.h.get(`${this.adm.classification}/admin/retrain/status`);
  }
}
