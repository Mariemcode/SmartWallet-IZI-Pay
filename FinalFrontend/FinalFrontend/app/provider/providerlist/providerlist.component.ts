import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ProviderService } from '../../services/provider/provider.service';
import { ProviderListStats, ProviderShare } from '../../models/Provider.model';

@Component({
  selector: 'app-providerlist',
  templateUrl: './providerlist.component.html',
  styleUrl: './providerlist.component.css'
})
export class ProviderlistComponent implements OnInit {

  stats:   ProviderListStats | null = null;
  loading = false;
  error   = false;

  fromDate = '';
  toDate   = '';

  // ── Arcs SVG ──────────────────────────────────────────────────────────
  countArcs:  { arc: string; color: string }[] = [];
  amountArcs: { arc: string; color: string }[] = [];

  // ── Hover ─────────────────────────────────────────────────────────────
  hoveredCount:  number | null = null;
  hoveredAmount: number | null = null;

  // ── Palette ───────────────────────────────────────────────────────────
  private readonly colors: string[] = [
    '#1565c0','#c62828','#2e7d32','#6a1b9a','#e65100',
    '#00838f','#4527a0','#558b2f','#ad1457','#00695c',
    '#f9a825','#0277bd','#4e342e','#37474f','#6d4c41',
  ];

  constructor(
    private providerService: ProviderService,
    private router: Router
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading      = true;
    this.error        = false;
    this.hoveredCount  = null;
    this.hoveredAmount = null;

    const from = this.fromDate ? this.fromDate + 'T00:00:00' : undefined;
    const to   = this.toDate   ? this.toDate   + 'T23:59:59' : undefined;

    this.providerService.getProviderListStats(from, to).subscribe({
      next: (data) => {
        this.stats = data;
        this.buildArcs(data.shares);
        this.loading = false;
      },
      error: () => { this.error = true; this.loading = false; }
    });
  }

  private buildArcs(shares: ProviderShare[]): void {
    this.countArcs  = this.buildDonutArcs(shares.map(s => s.percentCount));
    this.amountArcs = this.buildDonutArcs(shares.map(s => s.percentAmount));
  }

  private buildDonutArcs(percents: number[]): { arc: string; color: string }[] {
    const arcs: { arc: string; color: string }[] = [];
    let current = 0;
    percents.forEach((pct, i) => {
      const start = current;
      const end   = current + pct * 3.6;
      arcs.push({
        arc:   this.describeArc(150, 150, 110, start, end),
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

  getColor(i: number): string {
    return this.colors[i % this.colors.length];
  }

  goToDetails(id: string): void {
    this.router.navigate(['//layout/provider/providers', id, 'stats']);
  }

  formatAmount(v: number): string {
    return new Intl.NumberFormat('fr-TN', {
      minimumFractionDigits: 2, maximumFractionDigits: 2
    }).format(v) + ' TND';
  }

  formatCount(v: number): string {
    return new Intl.NumberFormat('fr-FR').format(v);
  }
}