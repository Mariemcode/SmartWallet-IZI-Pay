import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CategoryBreakdown } from '../../models/CategoryBreakdown';
import { AnalysisService } from '../../services/analyse/analysis.service';

@Component({
  selector: 'app-depense',
  templateUrl: './depense.component.html',
  styleUrl: './depense.component.css'
})
export class DepenseComponent implements OnInit {

  categories: CategoryBreakdown[] = [];
  loading = false;
  error   = false;

  fromDate = '';
  toDate   = '';

  hoveredIndex: number | null = null;

  private readonly palette: string[] = [
    '#1565c0','#c62828','#2e7d32','#6a1b9a','#e65100',
    '#00838f','#4527a0','#558b2f','#ad1457','#00695c',
    '#f9a825','#0277bd','#4e342e','#37474f','#6d4c41',
    '#1b5e20','#880e4f','#bf360c','#01579b','#33691e',
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
  };

  constructor(
    private analysisService: AnalysisService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.fromDate = params['from'] || '';
      this.toDate   = params['to']   || '';
      this.load();
    });
  }

  load(): void {
    this.loading = true;
    this.error   = false;
    const from = this.fromDate ? this.fromDate + 'T00:00:00' : undefined;
    const to   = this.toDate   ? this.toDate   + 'T23:59:59' : undefined;

    this.analysisService.getExpenseByCategory(from, to).subscribe({
      next: (data) => {
        this.categories = data;
        this.assignColors();
        this.loading = false;
      },
      error: () => { this.error = true; this.loading = false; }
    });
  }

  // ── Navigation vers sous-catégories ───────────────────────────────────
  goToSubCategory(cat: CategoryBreakdown): void {
    this.router.navigate(['/layout/analyse/souscat'], {
      queryParams: {
        category: cat.category,
        type:     'depense',          // ← indique à SouscategoryComponent le type
        from:     this.fromDate || '',
        to:       this.toDate   || '',
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/layout/analyse/global'], {
      queryParams: { from: this.fromDate, to: this.toDate }
    });
  }

  private assignColors(): void {
    this.colorMap.clear();
    this.categories.forEach((cat, i) =>
      this.colorMap.set(cat.category, this.palette[i % this.palette.length]));
  }

  getColor(category: string): string {
    return this.colorMap.get(category) ?? this.palette[0];
  }

  getIcon(category: string): string {
    return this.categoryIcons[category] ?? 'bi-grid-fill';
  }

  getTotalAmount(): number {
    return this.categories.reduce((s, c) => s + c.totalAmount, 0);
  }

  getTotalCount(): number {
    return this.categories.reduce((s, c) => s + c.totalCount, 0);
  }

  getArc(index: number): string {
    let start = 0;
    for (let i = 0; i < index; i++) start += this.categories[i].percentAmount;
    const end = start + this.categories[index].percentAmount;
    return this.describeArc(150, 150, 110, start * 3.6, end * 3.6);
  }

  private describeArc(cx: number, cy: number, r: number,
                      startDeg: number, endDeg: number): string {
    if (Math.abs(endDeg - startDeg) >= 359.99) endDeg = startDeg + 359.99;
    const s  = this.polarToCartesian(cx, cy, r, startDeg);
    const e  = this.polarToCartesian(cx, cy, r, endDeg);
    const lg = endDeg - startDeg > 180 ? 1 : 0;
    return `M ${s.x} ${s.y} A ${r} ${r} 0 ${lg} 1 ${e.x} ${e.y}`;
  }

  private polarToCartesian(cx: number, cy: number, r: number, deg: number) {
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