import { HttpClient, HttpParams, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiResponse } from '../../../models/profile.model';
import {
    RecommendationFilters,
    PageResponse,
    RecommendationResponse,
    ManualRecommendationDTO,
    RecommendationStatusDTO,
    BulkApproveDTO,
    RecommendationPageResponse,
    ClientProfileDTO,
} from '../../../models/recommendation.models';
import { environment } from '../../../../environments/environment';

@Injectable({
    providedIn: 'root',
})
export class RecommendationService {
    private baseUrl = environment.api.recommendations;

    constructor(private http: HttpClient) {}

    /**
     * Liste paginée des recommandations avec filtres
     */
    getRecommendations(
        filters: RecommendationFilters,
    ): Observable<RecommendationPageResponse> {
        let params = new HttpParams()
            .set('offset', (filters.offset ?? 0).toString())
            .set('limit', (filters.limit ?? 50).toString());
        if (filters.status) params = params.set('status', filters.status);
        if (filters.profile) params = params.set('profile', filters.profile);

        return this.http
            .get<
                ApiResponse<{
                    recommendations: RecommendationResponse[];
                    count: number;
                }>
            >(this.baseUrl, { params })
            .pipe(
                map((res) => ({
                    recommendations: res.data.recommendations,
                    count: res.data.count,
                })),
            );
    }
    /**
     * Ajout manuel d'une recommandation
     * @param dto
     * @param adminUser (facultatif, header)
     */
    addManualRecommendation(
        dto: ManualRecommendationDTO,
        adminUser: string = 'admin',
    ): Observable<RecommendationResponse> {
        const headers = new HttpHeaders().set('X-Admin-User', adminUser);
        return this.http
            .post<ApiResponse<RecommendationResponse>>(this.baseUrl, dto, { headers })
            .pipe(map((res) => res.data));
    }

    /**
     * Mise à jour du statut (APPROVED ou REJECTED) d'une recommandation
     */
    updateRecommendationStatus(
        id: number,
        dto: RecommendationStatusDTO,
        adminUser: string = 'admin',
    ): Observable<RecommendationResponse> {
        const headers = new HttpHeaders().set('X-Admin-User', adminUser);
        return this.http
            .put<
                ApiResponse<RecommendationResponse>
            >(`${this.baseUrl}/${id}`, dto, { headers })
            .pipe(map((res) => res.data));
    }

    /**
     * Approuver une recommandation (avec note optionnelle)
     */
    approveRecommendation(
        id: number,
        note?: string,
        adminUser: string = 'admin',
    ): Observable<RecommendationResponse> {
        const headers = new HttpHeaders().set('X-Admin-User', adminUser);
        const body = note ? { note } : {};
        return this.http
            .patch<
                ApiResponse<RecommendationResponse>
            >(`${this.baseUrl}/${id}/approve`, body, { headers })
            .pipe(map((res) => res.data));
    }

    /**
     * Rejeter une recommandation (avec note optionnelle)
     */
    rejectRecommendation(
        id: number,
        note?: string,
        adminUser: string = 'admin',
    ): Observable<RecommendationResponse> {
        const headers = new HttpHeaders().set('X-Admin-User', adminUser);
        const body = note ? { note } : {};
        return this.http
            .patch<
                ApiResponse<RecommendationResponse>
            >(`${this.baseUrl}/${id}/reject`, body, { headers })
            .pipe(map((res) => res.data));
    }

    /**
     * Approbation en masse pour un profil (toutes les recommandations PENDING)
     */
    bulkApprove(
        profileName: string,
        adminUser: string = 'admin',
    ): Observable<{ approvedCount: number; message: string }> {
        const headers = new HttpHeaders().set('X-Admin-User', adminUser);
        const body: BulkApproveDTO = { profileName };
        return this.http
            .post<
                ApiResponse<{ approvedCount: number; message: string }>
            >(`${this.baseUrl}/bulk-approve`, body, { headers })
            .pipe(map((res) => res.data));
    }

    /**
     * Récupère toutes les recommandations d'un profil (sans pagination)
     */
    getRecommendationsForProfile(
        profileName: string,
    ): Observable<RecommendationResponse[]> {
        return this.http
            .get<
                ApiResponse<RecommendationResponse[]>
            >(`${this.baseUrl}/profile/${profileName}`)
            .pipe(map((res) => res.data));
    }

    /**
     * Récupère une recommandation par son ID
     */
    getRecommendationById(id: number): Observable<RecommendationResponse> {
        return this.http
            .get<ApiResponse<RecommendationResponse>>(`${this.baseUrl}/${id}`)
            .pipe(map((res) => res.data));
    }

    // recommendation.service.ts
    // recommendation.service.ts (extrait)
    generateDescription(offerCode: string): Observable<string> {
        return this.http
            .post<ApiResponse<{ description: string }>>(
                `${this.baseUrl}/generate-description`,
                { offerCode: offerCode }, // ← camelCase
            )
            .pipe(map((res) => res.data.description));
    }

    // recommendation.service.ts
    getClientsByProfile(
        profileName: string,
        page: number = 0,
        size: number = 10,
    ): Observable<{
        clients: ClientProfileDTO[];
        totalElements: number;
        totalPages: number;
        currentPage: number;
    }> {
        const params = new HttpParams()
            .set('page', page.toString())
            .set('size', size.toString());
        // ✅ Utilise le nouveau contrôleur /api/profiles
        return this.http
            .get<
                ApiResponse<any>
            >(`${this.baseUrl}/${encodeURIComponent(profileName)}/clients`, { params })
            .pipe(map((res) => res.data));
    }

    // ════════════════════════════════════════════════════════════════
    //  DIFFUSION FCM — recommandations marketing
    // ════════════════════════════════════════════════════════════════

    /**
     * Envoie l'offre approuvée à un client précis via FCM
     * (notification push individuelle).
     * POST /api/recommendations/{id}/send-to-client
     */
    sendToClient(
        recoId: number,
        clientId: string,
        customMessage?: string,
        adminUser: string = 'admin',
    ): Observable<any> {
        const headers = new HttpHeaders().set('X-Admin-User', adminUser);
        const body: any = { clientId };
        if (customMessage) body.customMessage = customMessage;
        return this.http
            .post<ApiResponse<any>>(
                `${this.baseUrl}/${recoId}/send-to-client`,
                body,
                { headers },
            )
            .pipe(map((res) => res.data));
    }

    /**
     * Diffuse l'offre approuvée à TOUT UN PROFIL via le topic FCM
     * `profile_{clusterId}` (1 publication = N destinataires).
     * POST /api/recommendations/{id}/send-to-profile
     *
     * Si clusterId n'est pas fourni, le backend utilisera celui stocké
     * dans la recommandation.
     */
    sendToProfile(
        recoId: number,
        clusterId?: number,
        customMessage?: string,
        adminUser: string = 'admin',
    ): Observable<{
        status: string;
        clusterId: number;
        topic: string;
        offerCode: string;
        title: string;
        message: string;
        estimatedRecipients?: number;
        fcmResponse?: string;
    }> {
        const headers = new HttpHeaders().set('X-Admin-User', adminUser);
        const body: any = {};
        if (clusterId !== undefined && clusterId !== null) body.clusterId = clusterId;
        if (customMessage) body.customMessage = customMessage;
        return this.http
            .post<ApiResponse<any>>(
                `${this.baseUrl}/${recoId}/send-to-profile`,
                body,
                { headers },
            )
            .pipe(map((res) => res.data));
    }
}
