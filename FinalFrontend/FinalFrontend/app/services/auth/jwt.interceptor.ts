import { Injectable } from '@angular/core';
import {
  HttpInterceptor, HttpRequest, HttpHandler, HttpEvent,
} from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * JwtInterceptor — injecte le Bearer token JWT stocké dans
 * localStorage sur chaque requête HTTP sortante.
 *
 * Utilisé par Keycloak / Spring Security via le Gateway 8222.
 */
@Injectable()
export class JwtInterceptor implements HttpInterceptor {

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = localStorage.getItem('jwt_token');
    if (token) {
      req = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` },
      });
    }
    return next.handle(req);
  }
}
