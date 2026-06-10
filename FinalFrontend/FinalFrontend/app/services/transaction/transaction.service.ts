import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Transaction } from '../../models/Transaction';
import { TransactionFilter } from '../../models/TransactionFilter';
import { environment } from '../../../environments/environment';


@Injectable({ providedIn: 'root' })
export class TransactionService {
  private apiUrl = environment.api.transaction;

  constructor(private http: HttpClient) {}

  getTransactionsByClientId(clientId: string): Observable<Transaction[]> {
    return this.http.get<Transaction[]>(
      `${this.apiUrl}/clients/${clientId}/transactions`
    );
  }

  getTransactionsFiltered(clientId: string, filter: TransactionFilter): Observable<Transaction[]> {
  let params = new HttpParams();
  if (filter.category) params = params.set('category', filter.category);
  if (filter.typeType)  params = params.set('typeType',  filter.typeType);
  if (filter.date)      params = params.set('date',      filter.date);   // ← une seule date

  return this.http.get<Transaction[]>(
    `${this.apiUrl}/clients/${clientId}/transactions/filter`,
    { params }
  );
}

  getCategories(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/categories`);
  }
}