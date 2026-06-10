import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import {
  ApiResponse,
  HealthDTO,
  PredictRequestDTO,
  PredictResponseDTO,
  BatchPredictRequestDTO,
  BatchPredictResponseDTO,
  ProfileSummaryDTO,
  ClientProfileDTO,
  KpiSummaryDTO,
  DriftStatusDTO,
  MigrationSummaryDTO,
  ProfileTransactionsCountDTO,
  ProfileCategoryDTO,
  ModelRunDTO,
  MonitoringAlertDTO,
  ClientWithProfileDTO,
  Page,
} from '../../models/profile.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class ProfileService {
  private baseUrl = environment.api.classification;

  constructor(private http: HttpClient) {}

  getHealth(): Observable<ApiResponse<HealthDTO>> {
    return this.http.get<ApiResponse<HealthDTO>>(`${this.baseUrl}/health`);
  }

  predict(request: PredictRequestDTO): Observable<ApiResponse<PredictResponseDTO>> {
    return this.http.post<ApiResponse<PredictResponseDTO>>(`${this.baseUrl}/predict`, request);
  }

  batchPredict(request: BatchPredictRequestDTO): Observable<ApiResponse<BatchPredictResponseDTO>> {
    return this.http.post<ApiResponse<BatchPredictResponseDTO>>(`${this.baseUrl}/batch`, request);
  }

  getProfilesSummary(): Observable<ApiResponse<ProfileSummaryDTO[]>> {
    return this.http.get<ApiResponse<ProfileSummaryDTO[]>>(`${this.baseUrl}/profiles/summary`);
  }

  getProfileDetail(profileId: number): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.baseUrl}/profiles/${profileId}`);
  }

  getClientProfile(clientId: string): Observable<ApiResponse<ClientProfileDTO>> {
    return this.http.get<ApiResponse<ClientProfileDTO>>(`${this.baseUrl}/clients/${clientId}`);
  }

  getClientHistory(clientId: string): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.baseUrl}/clients/${clientId}/history`);
  }

  getHighChurnClients(threshold: number = 0.5): Observable<ApiResponse<ClientProfileDTO[]>> {
    return this.http.get<ApiResponse<ClientProfileDTO[]>>(`${this.baseUrl}/clients/churn?threshold=${threshold}`);
  }

  getKpiSummary(): Observable<ApiResponse<KpiSummaryDTO[]>> {
    return this.http.get<ApiResponse<KpiSummaryDTO[]>>(`${this.baseUrl}/kpi/summary`);
  }

  getDriftStatus(): Observable<ApiResponse<DriftStatusDTO[]>> {
    return this.http.get<ApiResponse<DriftStatusDTO[]>>(`${this.baseUrl}/drift/status`);
  }

  getMigrationsSummary(): Observable<ApiResponse<MigrationSummaryDTO[]>> {
    return this.http.get<ApiResponse<MigrationSummaryDTO[]>>(`${this.baseUrl}/migrations/summary`);
  }

  triggerRetrain(adminUser: string): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.baseUrl}/admin/retrain`, { admin_user: adminUser });
  }

  getRetrainStatus(): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.baseUrl}/admin/retrain/status`);
  }

  getTransactionsCountByProfile(): Observable<ApiResponse<ProfileTransactionsCountDTO[]>> {
    return this.http.get<ApiResponse<ProfileTransactionsCountDTO[]>>(`${this.baseUrl}/profiles/transactions-count`);
  }

  getCategoriesByProfile(profileId: number): Observable<ApiResponse<ProfileCategoryDTO[]>> {
    return this.http.get<ApiResponse<ProfileCategoryDTO[]>>(`${this.baseUrl}/profiles/${profileId}/categories`);
  }

  getModelRuns(): Observable<ApiResponse<ModelRunDTO[]>> {
    return this.http.get<ApiResponse<ModelRunDTO[]>>(`${this.baseUrl}/drift/status`);
  }

  getMonitoringAlerts(): Observable<ApiResponse<MonitoringAlertDTO[]>> {
    return this.http.get<ApiResponse<MonitoringAlertDTO[]>>(`${this.baseUrl}/monitoring/alerts`);
  }

  getAllProfileNames(): Observable<string[]> {
    return this.getProfilesSummary().pipe(
      map((response) => {
        const summaries = response?.data;
        if (!summaries || !Array.isArray(summaries)) return [];
        const names = summaries.map((s) => s.profile_name).filter((n) => n && n.trim().length > 0);
        return [...new Set(names)];
      }),
    );
  }

  // ⭐ Méthode pour obtenir les clients paginés d'un cluster
  getClientsByCluster(clusterId: number, page: number = 0, size: number = 10): Observable<ApiResponse<Page<ClientWithProfileDTO>>> {
    const url = `${this.baseUrl}/clusters/${clusterId}/clients?page=${page}&size=${size}`;
    console.log('🔍 Appel GET clients :', url);
    return this.http.get<ApiResponse<Page<ClientWithProfileDTO>>>(url);
  }
}