import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
  ApiResponse,
  ProfileCategoryDTO,
  ProfileTransactionsCountDTO,
  Page,
  ClientWithProfileDTO
} from '../../models/profile.model';
import { ProfileService } from '../../services/profile/profile.service';

@Component({
  selector: 'app-profiledetail',
  templateUrl: './profiledetail.component.html',
  styleUrls: ['./profiledetail.component.css']
})
export class ProfiledetailComponent implements OnInit {
  profileId: number | null = null;
  profile: any = null;
  transactionsCount: ProfileTransactionsCountDTO | null = null;
  categories: ProfileCategoryDTO[] = [];
  loading = true;
  error = false;

  // ⚙️ Clients paginés
  clients: ClientWithProfileDTO[] = [];
  clientsPageInfo: Page<ClientWithProfileDTO> | null = null;
  clientsLoading = false;
  currentPage = 0;
  pageSize = 10;

  Math = Math;
  private pendingCalls = 3; // profile, transactions, categories

  private colors: string[] = [
    '#1565c0', '#c62828', '#2e7d32', '#6a1b9a', '#e65100',
    '#00838f', '#4527a0', '#558b2f', '#ad1457', '#00695c',
    '#f9a825', '#0277bd', '#4e342e', '#37474f', '#6d4c41'
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private profileService: ProfileService
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.profileId = +params['id'];
      if (this.profileId !== null && !isNaN(this.profileId)) {
        this.loadAllData();
      } else {
        this.error = true;
        this.loading = false;
      }
    });
  }

  private loadAllData(): void {
    this.loading = true;
    this.error = false;
    this.pendingCalls = 3;

    // 1. Détail du profil
    this.profileService.getProfileDetail(this.profileId!).subscribe({
      next: (res: ApiResponse<any>) => {
        if (res.success && res.data) {
          this.profile = res.data;
        } else {
          this.error = true;
        }
        this.onCallComplete();
      },
      error: () => {
        this.error = true;
        this.onCallComplete();
      }
    });

    // 2. Transactions count
    this.profileService.getTransactionsCountByProfile().subscribe({
      next: (res: ApiResponse<ProfileTransactionsCountDTO[]>) => {
        if (res.success && res.data) {
          this.transactionsCount = res.data.find(t => t.cluster_id === this.profileId) ?? null;
        }
        this.onCallComplete();
      },
      error: () => this.onCallComplete()
    });

    // 3. Catégories (ne bloque pas en cas d'erreur)
    this.profileService.getCategoriesByProfile(this.profileId!).subscribe({
      next: (res: ApiResponse<ProfileCategoryDTO[]>) => {
        if (res.success && res.data) {
          this.categories = res.data;
        }
        this.onCallComplete();
      },
      error: (err) => {
        console.warn('⚠️ Catégories indisponibles pour le cluster', this.profileId, err);
        this.categories = [];
        this.onCallComplete();
      }
    });

    // 4. Chargement des clients (appel indépendant)
    this.loadClients();
  }

  private onCallComplete(): void {
    this.pendingCalls--;
    if (this.pendingCalls <= 0) {
      this.loading = false;
    }
  }

  // 📋 Récupère les clients paginés
  loadClients(): void {
  if (this.profileId === null) {
    console.warn('profileId est null, impossible de charger les clients');
    this.clientsLoading = false;
    return;
  }
  this.clientsLoading = true;
  console.log(`Chargement des clients pour le cluster ${this.profileId}, page ${this.currentPage}`);

  this.profileService.getClientsByCluster(this.profileId, this.currentPage, this.pageSize)
    .subscribe({
      next: (res: ApiResponse<Page<ClientWithProfileDTO>>) => {
        console.log('✅ Réponse clients reçue :', res);
        if (res.success && res.data) {
          this.clients = res.data.content || [];
          this.clientsPageInfo = res.data;
          console.log(`📋 ${this.clients.length} clients chargés sur ${res.data.totalElements}`);
        } else {
          console.warn('⚠️ Réponse sans succès ou data manquante', res);
          this.clients = [];
          this.clientsPageInfo = null;
        }
        this.clientsLoading = false;
      },
      error: (err) => {
        console.error('❌ Erreur HTTP lors du chargement des clients :', err);
        this.clientsLoading = false;
        this.clients = [];
      }
    });
}

  // 🧭 Pagination
  goToPage(page: number): void {
    if (page < 0 || (this.clientsPageInfo && page >= this.clientsPageInfo.totalPages)) return;
    this.currentPage = page;
    this.loadClients();
  }

  goBack(): void {
    this.router.navigate(['/layout/profile/profiles']);
  }

  getColor(index: number): string {
    return this.colors[index % this.colors.length];
  }

  formatNumber(v: number): string {
    return new Intl.NumberFormat('fr-FR').format(v);
  }

  formatCurrency(v: number): string {
    return new Intl.NumberFormat('fr-TN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v) + ' TND';
  }

  formatPercent(v: number): string {
    return (v * 100).toFixed(1) + '%';
  }

  private get ltvMax(): number {
    return this.profile?.ltv_12m_optimiste || 1;
  }

  ltvPesWidth(p: any): number {
    return Math.max(0, (p.ltv_12m_pessimiste || 0) / this.ltvMax * 100);
  }

  ltvBaseWidth(p: any): number {
    const base = (p.ltv_12m_base || 0) / this.ltvMax * 100;
    return Math.max(0, base - this.ltvPesWidth(p));
  }

  ltvOptWidth(p: any): number {
    return Math.max(0, 100 - this.ltvPesWidth(p) - this.ltvBaseWidth(p));
  }

  private readonly CIRC = 2 * Math.PI * 70;

  getDonutDash(pct: number): string {
    const filled = (pct / 100) * this.CIRC;
    return `${filled} ${this.CIRC - filled}`;
  }

  getDonutOffset(index: number): string {
    const offset = this.categories
      .slice(0, index)
      .reduce((sum, cat) => sum + (cat.pct / 100) * this.CIRC, 0);
    return `${-offset}`;
  }
}