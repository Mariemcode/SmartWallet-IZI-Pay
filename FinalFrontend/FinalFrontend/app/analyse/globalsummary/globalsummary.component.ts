import { Component, OnInit } from '@angular/core';
import { GlobalSummary } from '../../models/global-summary.model';
import { AnalysisService } from '../../services/analyse/analysis.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-globalsummary',
  templateUrl: './globalsummary.component.html',
  styleUrl: './globalsummary.component.css'
})
export class GlobalsummaryComponent implements OnInit {

  summary: GlobalSummary | null = null;
  loading = false;
  error = false;

  // Filtres de date
  fromDate = '';
  toDate   = '';

  // Données pour le graphique circulaire SVG
  expenseArc = '';
  revenueArc = '';
  expenseArcCount = '';
  revenueArcCount = '';

  constructor(private analysisService: AnalysisService , private router: Router) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error   = false;

    const from = this.fromDate ? this.fromDate + 'T00:00:00' : undefined;
    const to   = this.toDate   ? this.toDate   + 'T23:59:59' : undefined;

    this.analysisService.getGlobalSummary(from, to).subscribe({
      next: (data) => {
        this.summary = data;
        this.buildArcs(data);
        this.loading = false;
      },
      error: () => {
        this.error   = true;
        this.loading = false;
      }
    });
  }

  // ── Génération des arcs SVG pour le donut chart ───────────────────────────

  private buildArcs(data: GlobalSummary): void {
    // Arc basé sur les montants
    this.expenseArc = this.describeArc(150, 150, 110, 0,
                        data.expensePercentAmount * 3.6);
    this.revenueArc = this.describeArc(150, 150, 110,
                        data.expensePercentAmount * 3.6, 360);

    // Arc basé sur le nombre de transactions
    this.expenseArcCount = this.describeArc(150, 150, 110, 0,
                             data.expensePercentCount * 3.6);
    this.revenueArcCount = this.describeArc(150, 150, 110,
                             data.expensePercentCount * 3.6, 360);
  }

  private polarToCartesian(cx: number, cy: number, r: number, deg: number) {
    const rad = (deg - 90) * Math.PI / 180;
    return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
  }

  private describeArc(cx: number, cy: number, r: number,
                      startDeg: number, endDeg: number): string {
    if (Math.abs(endDeg - startDeg) >= 360) endDeg = startDeg + 359.99;
    const s    = this.polarToCartesian(cx, cy, r, startDeg);
    const e    = this.polarToCartesian(cx, cy, r, endDeg);
    const large = endDeg - startDeg > 180 ? 1 : 0;
    return `M ${s.x} ${s.y} A ${r} ${r} 0 ${large} 1 ${e.x} ${e.y}`;
  }

  // ── Formatage ─────────────────────────────────────────────────────────────

  formatAmount(value: number): string {
    return new Intl.NumberFormat('fr-TN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value) + ' TND';
  }

  formatCount(value: number): string {
    return new Intl.NumberFormat('fr-FR').format(value);
  }

  goToExpense(): void {
  this.router.navigate(['/layout/analyse/depense'], {
    queryParams: {
      from: this.fromDate || '',
      to:   this.toDate   || ''
    }
  });
}

  goToRevenue(): void {
  this.router.navigate(['/layout/analyse/revenue'], {
    queryParams: {
      from: this.fromDate || '',
      to:   this.toDate   || ''
    }
  });
}
}
