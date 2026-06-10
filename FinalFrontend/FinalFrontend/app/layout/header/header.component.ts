import { Component, OnInit } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../services/auth/auth.service';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrl: './header.component.css',
})
export class HeaderComponent implements OnInit {

  today = new Date();
  currentUrl = '';
  dropdownOpen = false;        // menu déroulant "Recommandation"
  dropdown2Open = false;       // menu déroulant "IA Admin"
  userMenuOpen = false;        // menu utilisateur (avatar)
  userName = 'Administrateur';

  constructor(private router: Router, private auth: AuthService) {}

  ngOnInit(): void {
    // Récupérer le nom d'utilisateur depuis l'AuthService
    const u = this.auth.getCurrentUser();
    if (u) this.userName = u.name;

    this.currentUrl = this.router.url;
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe((e: any) => {
        this.currentUrl = e.urlAfterRedirects;
        this.closeAllMenus();
      });

    // Fermer les menus si on clique en dehors
    document.addEventListener('click', (event: MouseEvent) => {
      const target = event.target as HTMLElement;
      if (!target.closest('.dropdown')) {
        this.closeDropdown();
      }
      if (!target.closest('.nav-user-menu')) {
        this.closeUserMenu();
      }
    });
  }

  /** Ouvre/ferme le menu déroulant "Recommandation" */
  toggleDropdown(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.dropdownOpen = !this.dropdownOpen;
    this.dropdown2Open = false;
    this.userMenuOpen = false;
  }

  /** Ouvre/ferme le menu déroulant "IA Admin" */
  toggleDropdown2(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.dropdown2Open = !this.dropdown2Open;
    this.dropdownOpen = false;
    this.userMenuOpen = false;
  }

  /** Ouvre/ferme le menu utilisateur */
  toggleUserMenu(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.userMenuOpen = !this.userMenuOpen;
    this.dropdownOpen = false;
    this.dropdown2Open = false;
  }

  closeDropdown():  void { this.dropdownOpen = false; this.dropdown2Open = false; }
  closeUserMenu():  void { this.userMenuOpen  = false; }
  closeAllMenus():  void { this.closeDropdown(); this.closeUserMenu(); }

  /** Déconnexion */
  logout(): void {
    this.auth.logout();
  }

  /** True si la route active correspond à une section donnée */
  isDropdownActive(section: string): boolean {
    if (section === 'recommendation') {
      return this.currentUrl.includes('/layout/recommendation');
    }
    if (section === 'ia-admin') {
      return this.currentUrl.includes('/layout/ia-admin');
    }
    return false;
  }

  /** Initiales pour l'avatar */
  get userInitials(): string {
    const parts = this.userName.split(' ');
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return this.userName.slice(0, 2).toUpperCase();
  }

  /** Retourne le libellé de la page active */
  getActiveLabel(): string {
    if (this.currentUrl.includes('/client/detail')) return 'Clients / Détail';
    if (this.currentUrl.includes('/client'))         return 'Clients';
    if (this.currentUrl.includes('/utilisation'))    return 'Utilisation';
    if (this.currentUrl.includes('/recommendation')) return 'Recommandation';
    return 'Accueil';
  }
}
