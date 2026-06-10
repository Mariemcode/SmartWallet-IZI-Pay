import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: {
    email: string;
    role: 'ADMIN' | 'USER';
    name: string;
  };
}

/**
 * SmartWallet IZI Pay — Service d'authentification
 * =================================================
 * Pour la soutenance, l'auth est MOCKÉE :
 *   - email    : admin@admin.com
 *   - password : admin123
 *
 * En production, remplacer la méthode `login()` par un vrai appel HTTP
 * vers le backend (ex. /api/auth-service/login via la gateway).
 * Le pattern Service est déjà en place pour faciliter le branchement.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'sw_token';
  private readonly USER_KEY  = 'sw_user';

  constructor(private router: Router) {}

  /** Mock de login — délai de 1.2s pour simuler un appel réseau */
  login(req: LoginRequest): Observable<LoginResponse> {
    const ok =
      req.email === environment.authMock.email &&
      req.password === environment.authMock.password;

    if (!ok) {
      return throwError(() => ({
        status: 401,
        message: 'Identifiants incorrects. Vérifiez votre e-mail et mot de passe.',
      })).pipe(delay(1200));
    }

    const resp: LoginResponse = {
      token: 'mock-jwt-token-' + Date.now(),
      user: {
        email: req.email,
        role:  'ADMIN',
        name:  'Administrateur',
      },
    };
    return of(resp).pipe(delay(1200));
  }

  /** Persiste le token et l'utilisateur en localStorage */
  storeSession(resp: LoginResponse): void {
    localStorage.setItem(this.TOKEN_KEY, resp.token);
    localStorage.setItem(this.USER_KEY,  JSON.stringify(resp.user));
  }

  /** Déconnexion : nettoie la session et redirige vers /auth */
  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.router.navigate(['/auth']);
  }

  /** True si l'utilisateur est connecté */
  isAuthenticated(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }

  /** Récupère le token JWT (pour les intercepteurs HTTP) */
  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  /** Récupère l'utilisateur courant */
  getCurrentUser(): LoginResponse['user'] | null {
    const raw = localStorage.getItem(this.USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }
}
