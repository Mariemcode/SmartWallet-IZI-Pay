import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AnalysisService } from '../../services/analyse/analysis.service';
import { CategoryBreakdown } from '../../models/CategoryBreakdown';
import { SubCategoryBreakdownDTO } from '../../models/SubCategoryBreakdownDTO';

@Component({
  selector: 'app-souscategory',
  templateUrl: './souscategory.component.html',
  styleUrl: './souscategory.component.css'
})
export class SouscategoryComponent implements OnInit {

  subCategories: SubCategoryBreakdownDTO[] = [];
  subLoading = false;
  subError   = false;

  // ── Paramètres reçus depuis dépense/revenue ───────────────────────────
  categoryName = '';
  categoryType: 'depense' | 'revenue' = 'depense';
  fromDate     = '';
  toDate       = '';

  // ── Couleur selon le type ─────────────────────────────────────────────
  get accentColor(): string {
    return this.categoryType === 'depense' ? '#c62828' : '#2e7d32';
  }

  hoveredIndex: number | null = null;

  private readonly palette: string[] = [
    '#e53935','#8e24aa','#1e88e5','#43a047','#fb8c00',
    '#00acc1','#6d4c41','#f4511e','#3949ab','#00897b',
    '#fdd835','#d81b60','#5e35b1','#039be5','#7cb342',
    '#c0ca33','#00b0ff','#ff6d00','#aa00ff','#00bfa5',
  ];
  private colorMap: Map<string, string> = new Map();

  readonly categoryIcons: Record<string, string> = {
    'Shopping & Paiements'    : 'bi-bag-fill',
    'Transferts Envoyés'      : 'bi-send-fill',
    'Transferts Envoyes'      : 'bi-send-fill',
    'Factures & Services'     : 'bi-receipt-cutoff',
    'Frais & Commissions'     : 'bi-percent',
    'Dépôt & Retrait'         : 'bi-cash-stack',
    'Depot & Retrait'         : 'bi-cash-stack',
    'Recharge Téléphonique'   : 'bi-phone-fill',
    'Recharge Telephonique'   : 'bi-phone-fill',
    'Restaurants & Livraison' : 'bi-bag-heart-fill',
    'Voyages & Réservations'  : 'bi-airplane-fill',
    'Voyages & Reservations'  : 'bi-airplane-fill',
    'Education & Institutions': 'bi-mortarboard-fill',
    'Annulation & Correction' : 'bi-arrow-counterclockwise',
    'Argent Reçu'             : 'bi-wallet2',
    'Transferts Reçus'        : 'bi-box-arrow-in-down',
  };

  constructor(
    private analysisService: AnalysisService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.categoryName = params['category'] || '';
      this.categoryType = params['type'] === 'revenue' ? 'revenue' : 'depense';
      this.fromDate     = params['from'] || '';
      this.toDate       = params['to']   || '';

      if (this.categoryName) {
        this.load();
      }
    });
  }

  load(): void {
    this.subLoading = true;
    this.subError   = false;
    this.colorMap.clear();

    const from = this.fromDate ? this.fromDate + 'T00:00:00' : undefined;
    const to   = this.toDate   ? this.toDate   + 'T23:59:59' : undefined;

    // ── Appel selon le type ──────────────────────────────────────────────
    const obs$ = this.categoryType === 'revenue'
      ? this.analysisService.getRevenueSubCategories(this.categoryName, from, to)
      : this.analysisService.getExpenseSubCategories(this.categoryName, from, to);

    obs$.subscribe({
      next: (data: SubCategoryBreakdownDTO[]) => {
        this.subCategories = data;
        this.assignColors();
        this.subLoading = false;
      },
      error: () => { this.subError = true; this.subLoading = false; }
    });
  }

  goBack(): void {
    const backRoute = this.categoryType === 'revenue'
      ? '/layout/analyse/revenue'
      : '/layout/analyse/depense';

    this.router.navigate([backRoute], {
      queryParams: { from: this.fromDate, to: this.toDate }
    });
  }

  private assignColors(): void {
    this.colorMap.clear();
    this.subCategories.forEach((sub, i) =>
      this.colorMap.set(sub.subCategory, this.palette[i % this.palette.length]));
  }

  getColor(subCategory: string): string {
    return this.colorMap.get(subCategory) ?? this.palette[0];
  }

  getIcon(category: string): string {
    return this.categoryIcons[category] ?? 'bi-grid-fill';
  }

  getTotalAmount(): number {
    return this.subCategories.reduce((s, c) => s + c.totalAmount, 0);
  }

  getTotalCount(): number {
    return this.subCategories.reduce((s, c) => s + c.totalCount, 0);
  }

  getArc(index: number): string {
    let start = 0;
    for (let i = 0; i < index; i++) start += this.subCategories[i].percentAmount;
    const end = start + this.subCategories[index].percentAmount;
    return this.describeArc(150, 150, 110, start * 3.6, end * 3.6);
  }

  private describeArc(cx: number, cy: number, r: number,
                      startDeg: number, endDeg: number): string {
    if (Math.abs(endDeg - startDeg) >= 359.99) endDeg = startDeg + 359.99;
    const s  = this.polar(cx, cy, r, startDeg);
    const e  = this.polar(cx, cy, r, endDeg);
    const lg = endDeg - startDeg > 180 ? 1 : 0;
    return `M ${s.x} ${s.y} A ${r} ${r} 0 ${lg} 1 ${e.x} ${e.y}`;
  }

  private polar(cx: number, cy: number, r: number, deg: number) {
    const rad = (deg - 90) * Math.PI / 180;
    return {
      x: +(cx + r * Math.cos(rad)).toFixed(3),
      y: +(cy + r * Math.sin(rad)).toFixed(3)
    };
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