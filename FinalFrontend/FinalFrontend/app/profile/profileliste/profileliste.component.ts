import { Component, OnInit, AfterViewInit } from '@angular/core';
import { Chart, PieController, ArcElement, Tooltip, Legend } from 'chart.js';
import { ProfileSummaryDTO, KpiSummaryDTO, ApiResponse } from '../../models/profile.model';
import { ProfileService } from '../../services/profile/profile.service';
import { Router } from '@angular/router';

// Enregistrement explicite des contrôleurs nécessaires pour le graphique en camembert
Chart.register(PieController, ArcElement, Tooltip, Legend);

@Component({
  selector: 'app-profileliste',
  templateUrl: './profileliste.component.html',
  styleUrls: ['./profileliste.component.css']
})
export class ProfilelisteComponent implements OnInit {
  profiles: ProfileSummaryDTO[] = [];
  loading = false;
  error = false;

  totalProfiles = 0;
  totalClients = 0;
  avgChurn = 0;
  totalLtv = 0;

  ltvPercentages: number[] = [];
  countArcs: { arc: string; color: string }[] = [];
  ltvArcs: { arc: string; color: string }[] = [];

  hoveredCount: number | null = null;
  hoveredLtv: number | null = null;

  private readonly colors: string[] = [
    '#1565c0', '#c62828', '#2e7d32', '#6a1b9a', '#e65100',
    '#00838f', '#4527a0', '#558b2f', '#ad1457', '#00695c',
    '#f9a825', '#0277bd', '#4e342e', '#37474f', '#6d4c41'
  ];

  constructor(
    private profileService: ProfileService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = false;
    this.profileService.getProfilesSummary().subscribe({
      next: (res: ApiResponse<ProfileSummaryDTO[]>) => {
        if (res.success && res.data) {
          this.profiles = res.data.sort((a, b) => a.cluster_id - b.cluster_id);
          this.computeAggregates();
          this.buildArcs();
        } else {
          this.error = true;
        }
        this.loading = false;
      },
      error: () => {
        this.error = true;
        this.loading = false;
      }
    });
  }

  private computeAggregates(): void {
    this.totalProfiles = this.profiles.length;
    this.totalClients = this.profiles.reduce((sum, p) => sum + p.n_clients, 0);
    const totalChurn = this.profiles.reduce((sum, p) => sum + (p.churn_score_30j || 0), 0);
    this.avgChurn = this.totalProfiles ? totalChurn / this.totalProfiles : 0;
    this.totalLtv = this.profiles.reduce((sum, p) => sum + (p.ltv_12m_base || 0), 0);
    
    this.ltvPercentages = this.profiles.map(p =>
      this.totalLtv > 0 ? ((p.ltv_12m_base || 0) / this.totalLtv) * 100 : 0
    );
  }

  private buildArcs(): void {
    const clientPercents = this.profiles.map(p => p.pct_clients);
    this.countArcs = this.buildDonutArcs(clientPercents);
    this.ltvArcs = this.buildDonutArcs(this.ltvPercentages);
  }

  private buildDonutArcs(percents: number[]): { arc: string; color: string }[] {
    const arcs: { arc: string; color: string }[] = [];
    let current = 0;
    percents.forEach((pct, i) => {
      const start = current;
      const end = current + pct * 3.6;
      arcs.push({
        arc: this.describeArc(150, 150, 110, start, end),
        color: this.colors[i % this.colors.length]
      });
      current = end;
    });
    return arcs;
  }

  private polarToCartesian(cx: number, cy: number, r: number, deg: number) {
    const rad = (deg - 90) * Math.PI / 180;
    return {
      x: +(cx + r * Math.cos(rad)).toFixed(3),
      y: +(cy + r * Math.sin(rad)).toFixed(3)
    };
  }

  private describeArc(cx: number, cy: number, r: number,
                      startDeg: number, endDeg: number): string {
    if (Math.abs(endDeg - startDeg) >= 360) endDeg = startDeg + 359.99;
    if (Math.abs(endDeg - startDeg) < 0.1) return '';
    const s = this.polarToCartesian(cx, cy, r, startDeg);
    const e = this.polarToCartesian(cx, cy, r, endDeg);
    const lg = endDeg - startDeg > 180 ? 1 : 0;
    return `M ${s.x} ${s.y} A ${r} ${r} 0 ${lg} 1 ${e.x} ${e.y}`;
  }

  getColor(i: number | null): string {
    if (i === null) return '#cccccc';
    return this.colors[i % this.colors.length];
  }

  getLtvPercent(profile: ProfileSummaryDTO): number {
    if (this.totalLtv === 0) return 0;
    return ((profile.ltv_12m_base || 0) / this.totalLtv) * 100;
  }

  getHoveredLtvPercent(): number {
    if (this.hoveredLtv !== null && this.profiles[this.hoveredLtv]) {
      return this.getLtvPercent(this.profiles[this.hoveredLtv]);
    }
    return 0;
  }

  getHoveredLtvValue(): number {
    if (this.hoveredLtv !== null && this.profiles[this.hoveredLtv]) {
      return this.profiles[this.hoveredLtv].ltv_12m_base || 0;
    }
    return 0;
  }

  goToDetails(clusterId: number): void {
    this.router.navigate(['/layout/profile/profiles', clusterId]);
  }

  goToModel(): void {
    this.router.navigate(['/layout/models/profilemodel']);
  }


  formatNumber(v: number): string {
    return new Intl.NumberFormat('fr-FR').format(v);
  }

  formatCurrency(v: number): string {
    return new Intl.NumberFormat('fr-TN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v) + ' TND';
  }
}