import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GlobalSummary } from '../../models/global-summary.model';
import { CategoryBreakdown } from '../../models/CategoryBreakdown';
import { SubCategoryBreakdownDTO } from '../../models/SubCategoryBreakdownDTO';
import { ProviderSummary } from '../../models/Provider.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AnalysisService {
  private readonly BASE_URL = environment.api.analysis;

  constructor(private http: HttpClient) {}

  getGlobalSummary(from?: string, to?: string): Observable<GlobalSummary> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<GlobalSummary>(`${this.BASE_URL}/global-summary`, {
      params,
    });
  }

  getExpenseByCategory(
    from?: string,
    to?: string,
  ): Observable<CategoryBreakdown[]> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<CategoryBreakdown[]>(
      `${this.BASE_URL}/expense-by-category`,
      { params },
    );
  }

  getRevenueByCategory(
    from?: string,
    to?: string,
  ): Observable<CategoryBreakdown[]> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<CategoryBreakdown[]>(
      `${this.BASE_URL}/revenue-by-category`,
      { params },
    );
  }

  getExpenseSubCategories(
    category: string,
    from?: string,
    to?: string,
  ): Observable<SubCategoryBreakdownDTO[]> {
    let params = new HttpParams().set('category', category);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<SubCategoryBreakdownDTO[]>(
      `${this.BASE_URL}/expense-sub-categories`,
      { params },
    );
  }

  getRevenueSubCategories(
    category: string,
    from?: string,
    to?: string,
  ): Observable<SubCategoryBreakdownDTO[]> {
    let params = new HttpParams().set('category', category);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<SubCategoryBreakdownDTO[]>(
      `${this.BASE_URL}/revenue-sub-categories`,
      { params },
    );
  }

}
