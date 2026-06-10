import { Component, OnInit } from '@angular/core';
import { FormGroup, FormBuilder } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { OfferResponse } from '../../models/recommendation.models';
import { OfferService } from '../../services/recommendation/offre/offer.service';
import { ProfileService } from '../../services/profile/profile.service';
import { Router } from '@angular/router';
import { RecommendationmetricsService } from '../../services/recommendation/RecommendationMetrics/recommendationmetrics.service';
import { NotificationService } from '../../services/recommendation/notif/notification.service';

@Component({
    selector: 'app-offerlist',
    templateUrl: './offerlist.component.html',
    styleUrls: ['./offerlist.component.css']
})
export class OfferlistComponent implements OnInit {

    allOffers: OfferResponse[] = [];
    filteredOffers: OfferResponse[] = [];
    loading = false;
    regenerating = false;
    alertMessages: { severity: 'warning' | 'critical'; message: string }[] = [];

    totalOffers = 0;
    activeOffers = 0;
    inactiveOffers = 0;
    manualOffers = 0;
    autoOffers = 0;
    avgOfferAge = 0;

    currentPage = 0;
    pageSize = 10;
    pageSizes = [10, 25, 50, 100];

    statusOptions = ['', 'ACTIVE', 'INACTIVE'];
    typeOptions: string[] = [];
    profileOptions: string[] = [];

    profileArcs: { path: string; color: string }[] = [];
    profileLabels: string[] = [];
    profileCounts: number[] = [];
    profilePercents: number[] = [];
    hoveredProfile: number | null = null;

    alertsExpanded = false;

    private readonly colors: string[] = [
        '#1565c0', '#c62828', '#2e7d32', '#6a1b9a', '#e65100',
        '#00838f', '#4527a0', '#558b2f', '#ad1457', '#00695c',
        '#f9a825', '#0277bd', '#4e342e', '#37474f', '#6d4c41',
    ];

    filterForm: FormGroup;

    constructor(
        private offerService: OfferService,
        private profileService: ProfileService,
        private metricsService: RecommendationmetricsService,
        private fb: FormBuilder,
        private router: Router,
        private notificationService: NotificationService
    ) {
        this.filterForm = this.fb.group({
            status: [''],
            type: [''],
            targetProfile: ['']
        });
    }

    ngOnInit(): void {
        console.log('🚀 [OfferList] ngOnInit');
        this.loadAllOffersAndAlerts();
        this.loadProfiles();
    }

    // ── SVG Donut helpers ─────────────────────────────────────────────

    private buildProfileDonut(): void {
        const profileCount = new Map<string, number>();
        this.allOffers.forEach(offer => {
            if (offer.targetProfiles && offer.targetProfiles.length > 0) {
                offer.targetProfiles.forEach(p => {
                    profileCount.set(p, (profileCount.get(p) || 0) + 1);
                });
            }
        });
        const universalCount = this.allOffers.filter(
            o => !o.targetProfiles || o.targetProfiles.length === 0
        ).length;
        if (universalCount > 0) profileCount.set('Tous profils', universalCount);
        const total = Array.from(profileCount.values()).reduce((s, v) => s + v, 0);
        this.profileLabels  = Array.from(profileCount.keys());
        this.profileCounts  = this.profileLabels.map(l => profileCount.get(l)!);
        this.profilePercents = this.profileCounts.map(c =>
            total > 0 ? Math.round((c / total) * 100) : 0
        );
        this.profileArcs = this.buildDonutArcs(
            this.profileCounts.map(c => total > 0 ? (c / total) * 100 : 0)
        );
    }

    private buildDonutArcs(percents: number[]): { path: string; color: string }[] {
        const arcs: { path: string; color: string }[] = [];
        let current = 0;
        percents.forEach((pct, i) => {
            const start = current;
            const end   = current + pct * 3.6;
            arcs.push({
                path:  this.describeArc(150, 150, 110, start, end),
                color: this.colors[i % this.colors.length]
            });
            current = end;
        });
        return arcs;
    }

    private polarToCartesian(cx: number, cy: number, r: number, deg: number) {
        const rad = (deg - 90) * Math.PI / 180;
        return { x: +(cx + r * Math.cos(rad)).toFixed(3), y: +(cy + r * Math.sin(rad)).toFixed(3) };
    }

    private describeArc(cx: number, cy: number, r: number, startDeg: number, endDeg: number): string {
        if (Math.abs(endDeg - startDeg) >= 360) endDeg = startDeg + 359.99;
        if (Math.abs(endDeg - startDeg) < 0.1) return '';
        const s  = this.polarToCartesian(cx, cy, r, startDeg);
        const e  = this.polarToCartesian(cx, cy, r, endDeg);
        const lg = endDeg - startDeg > 180 ? 1 : 0;
        return `M ${s.x} ${s.y} A ${r} ${r} 0 ${lg} 1 ${e.x} ${e.y}`;
    }

    // ── Data loading ──────────────────────────────────────────────────

    loadAllOffersAndAlerts(): void {
        console.log('📥 [OfferList] loadAllOffersAndAlerts() - DÉBUT');
        this.loading = true;

        console.log('📍 [OfferList] Appels:');
        console.log('  1. offerService.listOffers({limit:1000, offset:0}) → GET /api/offers?limit=1000&offset=0');
        console.log('  2. metricsService.getRecommendations(undefined, 2000) → GET /api/recommendations?limit=2000');

        forkJoin({
            offers:          this.offerService.listOffers({ limit: 1000, offset: 0 }),
            recommendations: this.metricsService.getRecommendations(undefined, 2000)
        }).subscribe({
            next: ({ offers, recommendations }) => {
                console.log('✅ [OfferList] Données reçues:');
                console.log('  - offers.offers.length:', offers?.offers?.length);
                console.log('  - offers.count:', offers?.count);
                console.log('  - recommendations.length:', recommendations?.length);

                this.allOffers      = offers.offers;
                this.totalOffers    = this.allOffers.length;
                this.activeOffers   = this.allOffers.filter(o => o.status === 'ACTIVE').length;
                this.inactiveOffers = this.allOffers.filter(o => o.status === 'INACTIVE').length;
                this.manualOffers   = this.allOffers.filter(o => o.generationMethod === 'manual').length;
                this.autoOffers     = this.totalOffers - this.manualOffers;

                console.log('📊 [OfferList] KPIs calculés:');
                console.log('  - totalOffers:', this.totalOffers);
                console.log('  - activeOffers:', this.activeOffers);
                console.log('  - inactiveOffers:', this.inactiveOffers);
                console.log('  - manualOffers:', this.manualOffers);
                console.log('  - autoOffers:', this.autoOffers);

                const activeList = this.allOffers.filter(o => o.status === 'ACTIVE');
                if (activeList.length) {
                    const totalDays = activeList.reduce((sum, o) => {
                        return sum + (Date.now() - new Date(o.createdAt).getTime()) / (1000 * 3600 * 24);
                    }, 0);
                    this.avgOfferAge = Math.round(totalDays / activeList.length);
                }

                this.updateTypeOptions();
                this.buildProfileDonut();
                this.applyFilters();
                this.computeAlerts(recommendations);
                this.loading = false;
                console.log('✅ [OfferList] loadAllOffersAndAlerts() - FIN');
            },
            error: (err) => {
                console.error('❌❌❌ [OfferList] ERREUR chargement:');
                console.error('  - status:', err?.status);
                console.error('  - message:', err?.message);
                console.error('  - url:', err?.url);
                console.error('  - error:', err?.error);
                this.loading = false;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // 🔄 BOUTON "RÉGÉNÉRER LES OFFRES" — LOGS DÉTAILLÉS
    // ═══════════════════════════════════════════════════════════════
    regenerateOffers(): void {
        console.log('══════════════════════════════════════════════');
        console.log('🔄 [OfferList] BOUTON "Régénérer les offres" CLIQUÉ');
        console.log('══════════════════════════════════════════════');

        if (this.regenerating) {
            console.log('⚠️ [OfferList] Déjà en cours, ignoré');
            return;
        }

        this.regenerating = true;
        console.log('📤 [OfferList] Appel de metricsService.regenerateOffers()');
        console.log('📍 URL appelée : POST /api/pipeline/offers/generate');
        console.log('📍 URL complète : http://localhost:8222/api/pipeline/offers/generate');

        this.notificationService.showInfo('Génération des offres en cours...');

        this.metricsService.regenerateOffers().subscribe({
            next: (response) => {
                console.log('══════════════════════════════════════════════');
                console.log('✅ [OfferList] RÉPONSE regenerateOffers REÇUE');
                console.log('  - response:', JSON.stringify(response, null, 2));
                console.log('══════════════════════════════════════════════');

                this.notificationService.showSuccess('Génération des offres lancée. Mise à jour dans 5 secondes...');
                setTimeout(() => {
                    console.log('🔄 [OfferList] Rechargement après 5s...');
                    this.loadAllOffersAndAlerts();
                    this.regenerating = false;
                }, 5000);
            },
            error: (err) => {
                console.log('══════════════════════════════════════════════');
                console.error('❌❌❌ [OfferList] ERREUR regenerateOffers ❌❌❌');
                console.log('══════════════════════════════════════════════');
                console.error('  1. Status HTTP :', err?.status);
                console.error('  2. Status Text :', err?.statusText);
                console.error('  3. Message :', err?.message);
                console.error('  4. URL appelée :', err?.url);
                console.error('  5. Nom erreur :', err?.name);

                if (err?.error) {
                    console.error('  6. Corps erreur (err.error) :');
                    console.error('     Type :', typeof err.error);
                    console.error('     Contenu :', JSON.stringify(err.error, null, 4));
                }

                if (err?.headers) {
                    console.error('  7. Headers réponse :');
                    err.headers.keys().forEach((key: string) => {
                        console.error(`     ${key}: ${err.headers.get(key)}`);
                    });
                }

                console.error('  8. Objet erreur complet :', err);
                console.log('══════════════════════════════════════════════');

                this.notificationService.showError('Erreur lors de la régénération des offres');
                this.regenerating = false;
            }
        });
    }

    computeAlerts(recommendations: any[]): void {
        this.alertMessages = [];
        const now = new Date();
        const recommendedCodes = new Set(recommendations.map(r => r.offerCode));

        for (const offer of this.allOffers) {
            if (offer.cashbackPct > 10) {
                this.alertMessages.push({
                    severity: 'warning',
                    message: `Offre ${offer.offerCode} : cashback élevé (${offer.cashbackPct}%)`
                });
            }
            if (offer.boost > 2.0) {
                this.alertMessages.push({
                    severity: 'warning',
                    message: `Offre ${offer.offerCode} : boost > 2.0 (${offer.boost})`
                });
            }
            if (offer.status === 'INACTIVE') {
                const daysInactive = Math.floor(
                    (now.getTime() - new Date(offer.updatedAt).getTime()) / (1000 * 3600 * 24)
                );
                if (daysInactive > 30) {
                    this.alertMessages.push({
                        severity: 'critical',
                        message: `Offre ${offer.offerCode} inactive depuis ${daysInactive} jours`
                    });
                }
            }
            if (offer.status === 'ACTIVE' && !recommendedCodes.has(offer.offerCode)) {
                this.alertMessages.push({
                    severity: 'warning',
                    message: `Offre ${offer.offerCode} active mais jamais recommandée`
                });
            }
        }

        if (this.alertMessages.length > 15) {
            const total = this.alertMessages.length;
            this.alertMessages = this.alertMessages.slice(0, 15);
            this.alertMessages.push({
                severity: 'warning',
                message: `... et ${total - 15} autres alertes non affichées`
            });
        }
    }

    updateTypeOptions(): void {
        const types = new Set(this.allOffers.map(o => o.type).filter(t => t));
        this.typeOptions = ['', ...Array.from(types)];
    }

    loadProfiles(): void {
        console.log('🔍 [OfferList] Chargement profils...');
        this.profileService.getAllProfileNames().subscribe({
            next: (names) => {
                console.log('✅ [OfferList] Profils:', names?.length, 'profils');
                this.profileOptions = names;
            },
            error: (err) => {
                console.error('❌ [OfferList] Erreur profils:', err);
            }
        });
    }

    applyFilters(): void {
        const f = this.filterForm.value;
        let result = [...this.allOffers];
        if (f.status) result = result.filter(o => o.status === f.status);
        if (f.type) result = result.filter(o => o.type === f.type);
        if (f.targetProfile) result = result.filter(o =>
            o.targetProfiles && o.targetProfiles.includes(f.targetProfile)
        );
        this.filteredOffers = result;
        this.currentPage = 0;
        console.log('🔍 [OfferList] Filtres appliqués:', f, '→', result.length, 'offres');
    }

    onFilterChange(): void { this.applyFilters(); }
    onPageChange(page: number): void { this.currentPage = page; }
    onPageSizeChange(size: number): void { this.pageSize = +size; this.currentPage = 0; }

    get paginatedOffers(): OfferResponse[] {
        const start = this.currentPage * this.pageSize;
        return this.filteredOffers.slice(start, start + this.pageSize);
    }

    get totalCount(): number  { return this.filteredOffers.length; }
    get totalPages(): number  { return Math.ceil(this.totalCount / this.pageSize); }

    createOffer(): void {
        this.router.navigate(['/layout/recommendation/offers/create']);
    }

    viewOfferDetails(offerCode: string): void {
        this.router.navigate(['/layout/recommendation/offer', offerCode]);
    }
}