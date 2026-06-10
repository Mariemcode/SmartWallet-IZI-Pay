import { Component, OnDestroy, OnInit, AfterViewInit,
         ViewChild, ElementRef, HostListener, ChangeDetectorRef } from '@angular/core';
import { Router } from '@angular/router';
import { DashboardResponse, DebitCredit, DailyActivity } from '../models/dashboard.model';
import { DashboardService } from '../services/dashboard/dashboard.service';
import { Subscription, interval, switchMap } from 'rxjs';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit, OnDestroy, AfterViewInit {

  @ViewChild('svgWrap') svgWrapRef!: ElementRef<HTMLDivElement>;

  data:    DashboardResponse | null = null;
  loading = true;
  error   = false;
  lastUpdate: Date = new Date();

  /* ── Courbe ──────────────────────────────────────────────────────── */
  svgW = 800;
  readonly svgH    = 240;
  readonly padTop  = 12;
  readonly padBot  = 22;
  readonly padLR   = 10;

  hoveredLine: number | null = null;

  lineCount  = '';
  lineAmount = '';
  areaCount  = '';
  areaAmount = '';
  gridYs: number[] = [];

  get maxCount(): number {
    if (!this.data?.dailyActivity?.length) return 1;
    return Math.max(...this.data.dailyActivity.map(d => d.count)) || 1;
  }
  get maxAmount(): number {
    if (!this.data?.dailyActivity?.length) return 1;
    return Math.max(...this.data.dailyActivity.map(d => d.totalAmount)) || 1;
  }

  /* ── Donuts ──────────────────────────────────────────────────────── */
  debitCreditCountArcs:  { arc: string; color: string }[] = [];
  debitCreditAmountArcs: { arc: string; color: string }[] = [];
  hoveredDCCount:  number | null = null;
  hoveredDCAmount: number | null = null;

  private refreshSub?:  Subscription;
  private resizeObs?:   ResizeObserver;
  private readonly REFRESH_MS = 30_000;

  private catColors = [
    '#1565c0','#c62828','#2e7d32','#6a1b9a','#e65100',
    '#00838f','#4527a0','#558b2f','#ad1457','#00695c'
  ];

  constructor(
    private svc: DashboardService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.load();
    this.refreshSub = interval(this.REFRESH_MS)
      .pipe(switchMap(() => this.svc.getDashboard()))
      .subscribe({ next: d => this.applyData(d), error: () => {} });
  }

  ngAfterViewInit(): void {
    // ResizeObserver : se déclenche dès que le conteneur a une largeur réelle
    if (this.svgWrapRef?.nativeElement) {
      this.resizeObs = new ResizeObserver(entries => {
        const w = entries[0]?.contentRect?.width;
        if (w && w > 0) {
          this.svgW = w;
          this.buildPaths();
          this.cdr.detectChanges();
        }
      });
      this.resizeObs.observe(this.svgWrapRef.nativeElement);
    }
  }

  ngOnDestroy(): void {
    this.refreshSub?.unsubscribe();
    this.resizeObs?.disconnect();
  }

  @HostListener('window:resize')
  onResize(): void {
    if (this.svgWrapRef?.nativeElement) {
      const w = this.svgWrapRef.nativeElement.clientWidth;
      if (w > 0) { this.svgW = w; this.buildPaths(); }
    }
  }

  load(): void {
    this.loading = true;
    this.error   = false;
    this.svc.getDashboard().subscribe({
      next: d => {
        this.applyData(d);
        this.loading = false;
        // Recalcul après rendu Angular (le *ngIf="data" vient de passer à true)
        setTimeout(() => {
          if (this.svgWrapRef?.nativeElement) {
            const w = this.svgWrapRef.nativeElement.clientWidth;
            if (w > 0) { this.svgW = w; this.buildPaths(); this.cdr.detectChanges(); }
          }
        }, 0);
      },
      error: () => { this.error = true; this.loading = false; }
    });
  }

  private applyData(d: DashboardResponse): void {
    this.data       = d;
    this.lastUpdate = new Date();
    this.buildDebitCreditArcs(d.debitCredit);
    // buildPaths sera appelé par le ResizeObserver ou le setTimeout dans load()
  }

  /* ── Construction des chemins SVG ───────────────────────────────── */
  buildPaths(): void {
    const pts = this.data?.dailyActivity;
    if (!pts?.length) return;

    const plotH = this.svgH - this.padTop - this.padBot;
    const n     = pts.length;

    const xOf  = (i: number) =>
      this.padLR + i * (this.svgW - 2 * this.padLR) / (n - 1 || 1);
    const yOfC = (v: number) =>
      this.padTop + plotH - (v / this.maxCount)  * plotH;
    const yOfA = (v: number) =>
      this.padTop + plotH - (v / this.maxAmount) * plotH;

    this.lineCount  = this.smoothPath(pts.map((p, i) => [xOf(i), yOfC(p.count)]));
    this.lineAmount = this.smoothPath(pts.map((p, i) => [xOf(i), yOfA(p.totalAmount)]));

    const baseY = this.padTop + plotH;
    this.areaCount  = this.lineCount  + ` L${xOf(n-1)},${baseY} L${xOf(0)},${baseY} Z`;
    this.areaAmount = this.lineAmount + ` L${xOf(n-1)},${baseY} L${xOf(0)},${baseY} Z`;

    this.gridYs = [
      this.padTop,
      this.padTop + plotH / 2,
      this.padTop + plotH
    ];
  }

  private smoothPath(pts: [number, number][]): string {
    if (pts.length < 2) return `M${pts[0][0]},${pts[0][1]}`;
    let d = `M${pts[0][0]},${pts[0][1]}`;
    for (let i = 0; i < pts.length - 1; i++) {
      const [x0, y0] = pts[i];
      const [x1, y1] = pts[i + 1];
      const cpX = (x0 + x1) / 2;
      d += ` C${cpX},${y0} ${cpX},${y1} ${x1},${y1}`;
    }
    return d;
  }

  /* ── Helpers coordonnées ─────────────────────────────────────────── */
  ptX(i: number): number {
    const n = this.data?.dailyActivity?.length || 1;
    return this.padLR + i * (this.svgW - 2 * this.padLR) / (n - 1 || 1);
  }

  ptCountY(i: number): number {
    const plotH = this.svgH - this.padTop - this.padBot;
    const v = this.data?.dailyActivity?.[i]?.count ?? 0;
    return this.padTop + plotH - (v / this.maxCount) * plotH;
  }

  ptAmountY(i: number): number {
    const plotH = this.svgH - this.padTop - this.padBot;
    const v = this.data?.dailyActivity?.[i]?.totalAmount ?? 0;
    return this.padTop + plotH - (v / this.maxAmount) * plotH;
  }

  tooltipTransform(i: number): string {
    const ttW = 160, margin = 8;
    let tx = this.ptX(i) + margin;
    if (tx + ttW > this.svgW - 4) tx = this.ptX(i) - ttW - margin;
    const ty = Math.max(4,
      Math.min(this.ptCountY(i), this.ptAmountY(i)) - 76);
    return `translate(${tx},${ty})`;
  }

  /* ── Donuts ──────────────────────────────────────────────────────── */
  private buildDebitCreditArcs(dc: DebitCredit): void {
    this.debitCreditCountArcs = [
      { arc: this.buildArc(0, dc.debitPctCount * 3.6),   color: '#c62828' },
      { arc: this.buildArc(dc.debitPctCount * 3.6, 360), color: '#2e7d32' }
    ];
    this.debitCreditAmountArcs = [
      { arc: this.buildArc(0, dc.debitPctAmount * 3.6),   color: '#c62828' },
      { arc: this.buildArc(dc.debitPctAmount * 3.6, 360), color: '#2e7d32' }
    ];
  }

  private buildArc(startDeg: number, endDeg: number): string {
    const cx = 150, cy = 150, r = 110;
    if (Math.abs(endDeg - startDeg) >= 360) endDeg = startDeg + 359.99;
    if (Math.abs(endDeg - startDeg) < 0.1)  return '';
    const toXY = (deg: number) => {
      const rad = (deg - 90) * Math.PI / 180;
      return {
        x: +(cx + r * Math.cos(rad)).toFixed(3),
        y: +(cy + r * Math.sin(rad)).toFixed(3)
      };
    };
    const s = toXY(startDeg), e = toXY(endDeg);
    return `M ${s.x} ${s.y} A ${r} ${r} 0 ${endDeg - startDeg > 180 ? 1 : 0} 1 ${e.x} ${e.y}`;
  }

  /* ── Navigation ─────────────────────────────────────────────────── */
  goToProviders():                void { this.router.navigate(['/layout/provider/providers']); }
  goToClients():                  void { this.router.navigate(['/layout/client/clients']); }
  goToProviderDetail(id: string): void { this.router.navigate(['/layout/provider/providers', id, 'stats']); }
  goToAnalyse():                  void { this.router.navigate(['/layout/analyse/global']); }

  /* ── Couleurs ────────────────────────────────────────────────────── */
  getCatColor(i: number): string { return this.catColors[i % this.catColors.length]; }

  /* ── Formatters ──────────────────────────────────────────────────── */
  formatAmount(v: number): string {
    return new Intl.NumberFormat('fr-TN', {
      minimumFractionDigits: 2, maximumFractionDigits: 2
    }).format(v) + ' TND';
  }
  formatCount(v: number): string {
    return new Intl.NumberFormat('fr-FR').format(v);
  }
  formatTime(d: Date): string {
    return d.toLocaleTimeString('fr-FR', {
      hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
  }
  providerInitials(name: string): string {
    return name.substring(0, 2).toUpperCase();
  }
  shortAmount(v: number): string {
    if (v >= 1_000_000) return (v / 1_000_000).toFixed(1) + ' M';
    if (v >= 1_000)     return (v / 1_000).toFixed(0)     + ' k';
    return v.toFixed(0);
  }
}