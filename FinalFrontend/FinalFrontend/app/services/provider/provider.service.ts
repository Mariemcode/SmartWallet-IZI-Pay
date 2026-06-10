import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Provider,
  ProviderDetail,
  ProviderListStats,
  ProviderStats,
  ProviderSummary,
} from '../../models/Provider.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ProviderService {
  private api = environment.api.providers;

  constructor(private http: HttpClient) {}

  getAllProviders(): Observable<Provider[]> {
    // ← type explicite
    return this.http.get<Provider[]>(this.api);
  }

  getProviderStats(id: string): Observable<ProviderStats> {
    // ← type explicite
    return this.http.get<ProviderStats>(`${this.api}/${id}/stats`);
  }

  // provider.service.ts — ajouter
  getAllProviderSummaries(): Observable<ProviderSummary[]> {
    return this.http.get<ProviderSummary[]>(`${this.api}/summaries`);
  }

  getProviderListStats(
    from?: string,
    to?: string,
  ): Observable<ProviderListStats> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<ProviderListStats>(`${this.api}/list-stats`, {
      params,
    });
  }

  getProviderDetail(
    id: string,
    from?: string,
    to?: string,
  ): Observable<ProviderDetail> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<ProviderDetail>(`${this.api}/${id}/detail`, {
      params,
    });
  }
}
