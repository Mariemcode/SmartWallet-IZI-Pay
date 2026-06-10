import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth/auth.service';

@Component({
  selector: 'app-auth',
  templateUrl: './auth.component.html',
  styleUrl: './auth.component.css',
})
export class AuthComponent {

  email        = '';
  password     = '';
  showPassword = false;
  rememberMe   = false;
  loading      = false;
  errorMessage = '';

  emailTouched    = false;
  passwordTouched = false;

  constructor(
    private router: Router,
    private auth: AuthService,
  ) {
    // Si déjà connecté, redirige directement
    if (this.auth.isAuthenticated()) {
      this.router.navigate(['/layout/dashboard']);
    }
  }

  /* ── Validation ──────────────────────────────────────────────────── */

  get emailValid(): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email);
  }

  get passwordValid(): boolean {
    return this.password.length >= 6;
  }

  get canSubmit(): boolean {
    return this.emailValid && this.passwordValid;
  }

  onEmailBlur():    void { this.emailTouched    = true; }
  onPasswordBlur(): void { this.passwordTouched = true; }
  onEmailInput():   void { if (this.emailTouched)    this.errorMessage = ''; }
  onPasswordInput():void { if (this.passwordTouched) this.errorMessage = ''; }

  /* ── Soumission ──────────────────────────────────────────────────── */

  onSubmit(): void {
    this.emailTouched    = true;
    this.passwordTouched = true;

    if (!this.canSubmit) return;

    this.loading      = true;
    this.errorMessage = '';

    this.auth.login({ email: this.email, password: this.password }).subscribe({
      next: (resp) => {
        this.auth.storeSession(resp);
        this.router.navigate(['/layout/dashboard']);
      },
      error: (err) => {
        this.errorMessage = err.message || 'Erreur de connexion. Veuillez réessayer.';
        this.loading = false;
      },
    });
  }
}
