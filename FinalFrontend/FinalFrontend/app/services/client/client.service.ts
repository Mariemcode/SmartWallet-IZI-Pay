import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Client } from '../../models/Client';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ClientService {
  /**
   * Utilise le préfixe legacy /api/admin-service/clients/** qui est rewrite
   * par la gateway vers /api/clients/** côté Spring Boot client-dashboard.
   * Cela évite toute ambiguïté avec les routes mobile (POST /api/clients).
   */
  private apiUrl = environment.api.adminClients;

  constructor(private http: HttpClient) {}

  getAllClients(): Observable<Client[]> {
    return this.http.get<Client[]>(this.apiUrl);
  }

  searchClients(query: string): Observable<Client[]> {
    return this.http.get<Client[]>(
      `${this.apiUrl}/search?q=${encodeURIComponent(query)}`
    );
  }

  getClientById(id: string): Observable<Client> {
    return this.http.get<Client>(`${this.apiUrl}/${id}`);
  }
}
