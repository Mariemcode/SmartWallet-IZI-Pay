import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiResponse } from '../../../models/profile.model';
import {
  PageResponse,
  OfferResponse,
  OfferRequest,
  OfferUpdate,
  OfferStatusUpdate,
} from '../../../models/recommendation.models';
import { environment } from '../../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class OfferService {
  private baseUrl = environment.api.offers;

  constructor(private http: HttpClient) {}

  /**
   * Liste paginée des offres avec filtres
   */
  listOffers(params: {
    status?: string;
    type?: string;
    provider?: string;
    category?: string;
    offset?: number;
    limit?: number;
  }): Observable<PageResponse<OfferResponse>> {
    let httpParams = new HttpParams();
    if (params.status) httpParams = httpParams.set('status', params.status);
    if (params.type) httpParams = httpParams.set('type', params.type);
    if (params.provider)
      httpParams = httpParams.set('provider', params.provider);
    if (params.category)
      httpParams = httpParams.set('category', params.category);
    if (params.offset !== undefined)
      httpParams = httpParams.set('offset', params.offset.toString());
    if (params.limit !== undefined)
      httpParams = httpParams.set('limit', params.limit.toString());

    return this.http
      .get<
        ApiResponse<PageResponse<OfferResponse>>
      >(this.baseUrl, { params: httpParams })
      .pipe(map((response) => response.data));
  }

  /**
   * Récupère une offre par son code
   */
  getOffer(offerCode: string): Observable<OfferResponse> {
    return this.http
      .get<ApiResponse<OfferResponse>>(`${this.baseUrl}/${offerCode}`)
      .pipe(map((response) => response.data));
  }

  /**
   * Crée une nouvelle offre
   */
  createOffer(offer: OfferRequest): Observable<OfferResponse> {
    return this.http
      .post<ApiResponse<OfferResponse>>(this.baseUrl, offer)
      .pipe(map((response) => response.data));
  }

  /**
   * Met à jour une offre existante
   */
  updateOffer(
    offerCode: string,
    offer: OfferUpdate,
  ): Observable<OfferResponse> {
    return this.http
      .put<ApiResponse<OfferResponse>>(`${this.baseUrl}/${offerCode}`, offer)
      .pipe(map((response) => response.data));
  }

  /**
   * Modifie le statut d'une offre
   */
  updateOfferStatus(
    offerCode: string,
    statusUpdate: OfferStatusUpdate,
  ): Observable<OfferResponse> {
    return this.http
      .patch<
        ApiResponse<OfferResponse>
      >(`${this.baseUrl}/${offerCode}/status`, statusUpdate)
      .pipe(map((response) => response.data));
  }

  /**
   * Supprime une offre
   */
  deleteOffer(offerCode: string): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.baseUrl}/${offerCode}`)
      .pipe(map(() => undefined));
  }
}
