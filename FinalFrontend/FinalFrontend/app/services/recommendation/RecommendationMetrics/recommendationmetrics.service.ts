import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, forkJoin, map, of } from 'rxjs';
import { ApiResponse } from '../../../models/profile.model';
import {
    MetricsSummaryDTO,
    GenerationRun,
    RecommendationScore,
    HealthAlert,
    GenerationRunRaw,
    ModelRun,
} from '../../../models/recommendation.models';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class RecommendationmetricsService {
    private apiBase = environment.gatewayUrl + '/api';

    constructor(private http: HttpClient) {}

    getMetrics(evaluationType: string = 'simulated'): Observable<MetricsSummaryDTO> {
        const params = new HttpParams().set('evaluation_type', evaluationType);
        return this.http.get<ApiResponse<MetricsSummaryDTO>>(`${this.apiBase}/metrics`, { params })
            .pipe(map(res => res.data));
    }

    getMetricsHistory(profile: string, limit: number = 10): Observable<MetricsSummaryDTO> {
        const params = new HttpParams().set('profile', profile).set('limit', limit.toString());
        return this.http.get<ApiResponse<MetricsSummaryDTO>>(`${this.apiBase}/metrics/history`, { params })
            .pipe(map(res => res.data));
    }

    private getGenerationRuns(limit: number = 20): Observable<{ runs: GenerationRun[] }> {
        const params = new HttpParams().set('limit', limit.toString());
        // ✅ Pointer vers FastAPI (port 8000) qui a /api/v5/generation-runs
        console.log('📦 Appel generation-runs → http://localhost:8000/api/v5/generation-runs');
        return this.http.get<any>(`http://localhost:8000/api/v5/generation-runs`, { params })
            .pipe(map(res => {
                console.log('📦 Réponse brute:', res);
                // FastAPI retourne { data: { runs: [...] } }
                const rawRuns = res?.data?.runs || res?.runs || [];
                console.log('📦 rawRuns:', rawRuns);
                const runs = rawRuns.map((raw: any) => this.mapToGenerationRun(raw));
                return { runs };
            }));
    }

    private mapToGenerationRun(raw: any): GenerationRun {
        return {
            runId: raw.run_id || raw.runId,
            startedAt: raw.started_at || raw.startedAt,
            finishedAt: raw.finished_at || raw.finishedAt,
            nProfiles: raw.n_profiles || raw.nProfiles,
            nOffersGen: raw.n_offers_gen || raw.nOffersGen,
            nOffersNew: raw.n_offers_new ?? raw.nOffersNew ?? 0,
            nOffersDeact: raw.n_offers_deact ?? raw.nOffersDeact ?? 0,
            status: raw.status,
            errorMsg: raw.error_msg || raw.errorMsg,
        };
    }

    getOfferGenerationRuns(limit: number = 20): Observable<{ runs: GenerationRun[] }> {
        return this.getGenerationRuns(limit);
    }

    getRecommendationGenerationRuns(limit: number = 20): Observable<{ runs: GenerationRun[] }> {
        return this.getGenerationRuns(limit);
    }

    getModelRuns(limit: number = 10): Observable<ModelRun[]> {
        const params = new HttpParams().set('limit', limit.toString());
        return this.http.get<any>(`http://localhost:8000/api/v5/model/runs`, { params })
            .pipe(map(res => {
                const rawRuns = res?.data?.runs || res?.runs || [];
                return rawRuns.map((raw: any) => ({
                    modelVersion: raw.model_version,
                    runAt: raw.run_at,
                    silhouetteScore: raw.silhouette_score,
                    daviesBouldin: raw.davies_bouldin,
                    calinskiHarabasz: raw.calinski_harabasz,
                    gbmTestF1: raw.gbm_test_f1,
                    psiMax: raw.psi_max,
                    psiStatus: raw.psi_status,
                    nClients: raw.n_clients,
                    nMixte: raw.n_mixte,
                    churnPctHighRisk: raw.churn_pct_high_risk,
                }));
            }));
    }

    getRecommendations(profile?: string, limit: number = 500): Observable<RecommendationScore[]> {
        let params = new HttpParams().set('limit', limit.toString());
        if (profile) params = params.set('profile', profile);
        return this.http.get<ApiResponse<{ recommendations: any[] }>>(`${this.apiBase}/recommendations`, { params })
            .pipe(map(res => {
                const recs = res.data?.recommendations || [];
                return recs.map(r => ({
                    profileName: r.profile_name || r.profileName,
                    offerCode: r.offer_code || r.offerCode,
                    offerTitle: r.offer_title || r.offerTitle || '',
                    score: r.score,
                }));
            }));
    }

    regenerateOffers(): Observable<{ started: boolean; message: string }> {
        return this.http.post<any>(`http://localhost:8000/api/v5/offers/generate`, {})
            .pipe(map(res => ({ started: true, message: res.message || 'Génération lancée' })));
    }

    regenerateRecommendations(): Observable<{ started: boolean; message: string }> {
        return this.http.post<any>(`http://localhost:8000/api/v5/recommendations/generate`, {})
            .pipe(map(res => ({ started: true, message: res.message || 'Génération lancée' })));
    }

    getScoreStandardDeviation(limit: number = 1000): Observable<number> {
        return this.getRecommendations(undefined, limit).pipe(
            map(recs => {
                const scores = recs.map(r => r.score);
                if (scores.length < 2) return 0;
                const mean = scores.reduce((a, b) => a + b, 0) / scores.length;
                const squaredDiffs = scores.map(s => Math.pow(s - mean, 2));
                const variance = squaredDiffs.reduce((a, b) => a + b, 0) / (scores.length - 1);
                return Math.sqrt(variance);
            })
        );
    }

    getHealthAlerts(): Observable<HealthAlert[]> {
        return forkJoin({
            metrics: this.getMetrics('simulated'),
            modelRuns: this.getModelRuns(1),
            generationRuns: this.getOfferGenerationRuns(5),
            scoreStdDev: this.getScoreStandardDeviation(1000),
        }).pipe(map(({ metrics, modelRuns, generationRuns, scoreStdDev }) => {
            const alerts: HealthAlert[] = [];
            return alerts;
        }));
    }

    sendNotifications(profileName: string): Observable<{ sent: number; profiles: string[]; sent_at: string }> {
        return this.http
            .post<ApiResponse<{ sent: number; profiles: string[]; sent_at: string }>>(
                `${this.apiBase}/pipeline/notifications/send`,
                { profile_filter: profileName }
            )
            .pipe(map(res => res.data));
    }
    generateDescription(offerCode: string): Observable<string> {
        return this.http.post<ApiResponse<{ description: string }>>(
            `${this.apiBase}/recommendations/generate-description`,
            { offerCode }
        ).pipe(map(res => res.data.description));
    }
}