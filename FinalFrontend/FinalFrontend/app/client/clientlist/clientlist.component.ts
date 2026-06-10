import { Component, OnInit } from '@angular/core';
import { Client } from '../../models/Client';
import { ClientService } from '../../services/client/client.service';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

@Component({
  selector: 'app-clientlist',
  templateUrl: './clientlist.component.html',
  styleUrl: './clientlist.component.css',
})
export class ClientlistComponent implements OnInit {
  clients: Client[] = [];
  searchTerm = '';
  loading = true;
  searching = false;
  searchFocused = false;
  error = '';

  // Pagination
  currentPage = 1;
  pageSize = 10;

  private searchSubject = new Subject<string>();
  // Palette de couleurs pour les avatars
  private avatarColors = [
    '#1565c0',
    '#c62828',
    '#2e7d32',
    '#6a1b9a',
    '#e65100',
    '#00838f',
    '#4527a0',
    '#ad1457',
    '#00695c',
    '#0277bd',
  ];

  getInitials(firstName: string, lastName: string): string {
    return ((firstName?.[0] || '') + (lastName?.[0] || '')).toUpperCase();
  }

  getAvatarColor(seed: string): string {
    let hash = 0;
    for (let i = 0; i < seed.length; i++) {
      hash = seed.charCodeAt(i) + ((hash << 5) - hash);
    }
    return this.avatarColors[Math.abs(hash) % this.avatarColors.length];
  }

  constructor(private clientService: ClientService) {}

  ngOnInit(): void {
    this.loadClients();
    this.searchSubject
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe((term) => this.performSearch(term));
  }

  loadClients(): void {
    this.loading = true;
    this.clientService.getAllClients().subscribe({
      next: (data) => {
        this.clients = data;
        this.loading = false;
        this.currentPage = 1;
      },
      error: () => {
        this.error = 'Erreur de chargement.';
        this.loading = false;
      },
    });
  }

  onSearch(): void {
    this.searchSubject.next(this.searchTerm.trim());
  }

  performSearch(term: string): void {
    if (!term) {
      this.loadClients();
      return;
    }
    this.searching = true;
    this.clientService.searchClients(term).subscribe({
      next: (data) => {
        this.clients = data;
        this.searching = false;
        this.currentPage = 1;
      },
      error: () => {
        this.error = 'Erreur de recherche.';
        this.searching = false;
      },
    });
  }

  clearSearch(): void {
    this.searchTerm = '';
    this.loadClients();
  }

  // ── Pagination ──
  get totalClients(): number {
    return this.clients.length;
  }

  get totalPages(): number {
    return Math.ceil(this.clients.length / this.pageSize);
  }

  get pagedClients(): Client[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.clients.slice(start, start + this.pageSize);
  }

  get pageNumbers(): number[] {
    const total = this.totalPages;
    const current = this.currentPage;
    const delta = 2;
    const range: number[] = [];

    for (
      let i = Math.max(1, current - delta);
      i <= Math.min(total, current + delta);
      i++
    ) {
      range.push(i);
    }
    return range;
  }

  goToPage(page: number): void {
    if (page < 1 || page > this.totalPages) return;
    this.currentPage = page;
  }

  onPageSizeChange(): void {
    this.currentPage = 1;
  }
}
