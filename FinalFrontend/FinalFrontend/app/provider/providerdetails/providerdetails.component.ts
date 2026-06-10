import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ProviderDetail, ProviderStats } from '../../models/Provider.model';
import { ProviderService } from '../../services/provider/provider.service';

@Component({
  selector: 'app-providerdetails',
  templateUrl: './providerdetails.component.html',
  styleUrl: './providerdetails.component.css',
})
export class ProviderdetailsComponent implements OnInit {
  detail: ProviderDetail | null = null;
  loading = true;
  error = false;
  activeTab: 'types' | 'clients' | 'distribution' = 'types';

  fromDate = '';
  toDate = '';

  smallArc = '';
  mediumArc = '';
  largeArc = '';

  private providerId = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private svc: ProviderService,
  ) {}

  ngOnInit(): void {
    this.providerId = this.route.snapshot.paramMap.get('id') || '';
    this.load();
  }

  load(): void {
    if (!this.providerId) return;
    this.loading = true;
    this.error = false;

    const from = this.fromDate ? this.fromDate + 'T00:00:00' : undefined;
    const to = this.toDate ? this.toDate + 'T23:59:59' : undefined;

    this.svc.getProviderDetail(this.providerId, from, to).subscribe({
      next: (d) => {
        this.detail = d;
        this.buildArcs(d);
        this.loading = false;
      },
      error: () => {
        this.error = true;
        this.loading = false;
      },
    });
  }

  resetFilter(): void {
    this.fromDate = '';
    this.toDate = '';
    this.load();
  }

  goBack(): void {
    this.router.navigate(['/layout/provider/providers']);
  }

  setTab(t: 'types' | 'clients' | 'distribution'): void {
    this.activeTab = t;
  }

  private buildArcs(d: ProviderDetail): void {
    this.smallArc = this.arcPath(0, d.smallPct * 3.6);
    this.mediumArc = this.arcPath(
      d.smallPct * 3.6,
      (d.smallPct + d.mediumPct) * 3.6,
    );
    this.largeArc = this.arcPath((d.smallPct + d.mediumPct) * 3.6, 360);
  }
  arcPath(startDeg: number, endDeg: number): string {
    const cx = 150,
      cy = 150,
      r = 110; // ← 300×300 viewBox, r=110
    if (Math.abs(endDeg - startDeg) >= 360) endDeg = startDeg + 359.99;
    if (Math.abs(endDeg - startDeg) < 0.1) return '';
    const toXY = (deg: number) => {
      const rad = ((deg - 90) * Math.PI) / 180;
      return {
        x: +(cx + r * Math.cos(rad)).toFixed(3),
        y: +(cy + r * Math.sin(rad)).toFixed(3),
      };
    };
    const s = toXY(startDeg);
    const e = toXY(endDeg);
    const large = endDeg - startDeg > 180 ? 1 : 0;
    return `M ${s.x} ${s.y} A ${r} ${r} 0 ${large} 1 ${e.x} ${e.y}`;
  }
  private polarToCartesian(cx: number, cy: number, r: number, deg: number) {
    const rad = ((deg - 90) * Math.PI) / 180;
    return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
  }

  arc(startDeg: number, endDeg: number): string {
    if (Math.abs(endDeg - startDeg) >= 360) endDeg = startDeg + 359.99;
    if (Math.abs(endDeg - startDeg) < 0.1) return '';
    const s = this.polarToCartesian(100, 100, 70, startDeg);
    const e = this.polarToCartesian(100, 100, 70, endDeg);
    const large = endDeg - startDeg > 180 ? 1 : 0;
    return `M ${s.x} ${s.y} A 70 70 0 ${large} 1 ${e.x} ${e.y}`;
  }

  getInitials(name: string): string {
    return name
      .split(' ')
      .map((w) => w[0])
      .join('')
      .substring(0, 2)
      .toUpperCase();
  }

  clientInitials(c: { firstName: string; lastName: string }): string {
    return ((c.firstName?.[0] || '') + (c.lastName?.[0] || '')).toUpperCase();
  }

  recurringPct(): number {
    if (!this.detail || this.detail.distinctClients === 0) return 0;
    return Math.round(
      (this.detail.recurringClients / this.detail.distinctClients) * 100,
    );
  }

  occasionalPct(): number {
    return 100 - this.recurringPct();
  }

  typePct(count: number): number {
    if (!this.detail || this.detail.totalTransactions === 0) return 0;
    return Math.round((count / this.detail.totalTransactions) * 100 * 10) / 10;
  }

  formatAmount(v: number): string {
    return (
      new Intl.NumberFormat('fr-TN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(v) + ' TND'
    );
  }

  formatCount(v: number): string {
    return new Intl.NumberFormat('fr-FR').format(v);
  }

  formatDate(s: string): string {
    if (!s) return '—';
    return new Date(s).toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  }
}
