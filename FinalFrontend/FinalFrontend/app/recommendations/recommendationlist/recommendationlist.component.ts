// recommendationlist.component.ts
import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormGroup, FormBuilder } from '@angular/forms';
import { RecommendationResponse } from '../../models/recommendation.models';
import { NotificationService } from '../../services/recommendation/notif/notification.service';
import { RecommendationService } from '../../services/recommendation/recommendation/recommendation.service';
import { RecommendationmetricsService } from '../../services/recommendation/RecommendationMetrics/recommendationmetrics.service';
import { ProfileService } from '../../services/profile/profile.service';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { WebSocketService } from '../../services/recommendation/recommendation/websocket.service';

@Component({
    selector: 'app-recommendationlist',
    templateUrl: './recommendationlist.component.html',
    styleUrls: ['./recommendationlist.component.css']
})
export class RecommendationlistComponent implements OnInit, OnDestroy {
    recommendations: RecommendationResponse[] = [];
    allRecommendations: RecommendationResponse[] = [];
    totalCount = 0;
    loading = false;
    regenerating = false;

    // KPI
    totalRecos = 0;
    approvedCount = 0;
    rejectedCount = 0;
    pendingCount = 0;
    avgScore = 0;
    approvalRate = 0;

    // Batch
    selectedItems: Set<number> = new Set();
    isAllSelected = false;

    currentPage = 0;
    pageSize = 10;
    pageSizes = [10, 25, 50];

    filterForm: FormGroup;
    profileOptions: string[] = [];

    // Donut chart
    profileArcs: { path: string; color: string }[] = [];
    profileLabels: string[] = [];
    profileCounts: number[] = [];
    profilePercents: number[] = [];
    hoveredProfile: number | null = null;

    // Alerts
    alertsExpanded = false;
    alertMessages: { severity: 'warning' | 'critical'; message: string }[] = [];

    private readonly colors: string[] = [
        '#1565c0', '#c62828', '#2e7d32', '#6a1b9a', '#e65100',
        '#00838f', '#4527a0', '#558b2f', '#ad1457', '#00695c',
        '#f9a825', '#0277bd', '#4e342e', '#37474f', '#6d4c41',
    ];

    private wsSubscription: Subscription | null = null;

    constructor(
        private recoService: RecommendationService,
        private metricsService: RecommendationmetricsService,
        private profileService: ProfileService,
        private fb: FormBuilder,
        private router: Router,
        private notificationService: NotificationService,
        private webSocketService: WebSocketService
    ) {
        this.filterForm = this.fb.group({
            status: [''],
            profile: ['']
        });
    }

    ngOnInit(): void {
        this.loadProfiles();
        this.loadRecommendations();
        this.loadAllRecommendationsForStats();
        this.initWebSocket();
    }

    ngOnDestroy(): void {
        if (this.wsSubscription) this.wsSubscription.unsubscribe();
        this.webSocketService.deactivate();
    }

    private initWebSocket(): void {
        this.webSocketService.activate();
        this.wsSubscription = this.webSocketService.watch('/topic/recommendations').subscribe({
            next: (message) => {
                const event = JSON.parse(message.body);
                this.notificationService.showInfo(`Recommandation ${event.id} : ${event.event}`);
                this.loadRecommendations();
                this.loadAllRecommendationsForStats();
            },
            error: (err) => console.error('WebSocket error', err)
        });
    }

    loadProfiles(): void {
        this.profileService.getAllProfileNames().subscribe({
            next: (names) => { this.profileOptions = ['', ...names]; },
            error: (err) => console.error('Erreur chargement profils', err)
        });
    }

    loadRecommendations(): void {
        this.loading = true;
        const filters = this.filterForm.value;
        this.recoService.getRecommendations({
            status: filters.status || undefined,
            profile: filters.profile || undefined,
            offset: this.currentPage * this.pageSize,
            limit: this.pageSize
        }).subscribe({
            next: (page) => {
                this.recommendations = page.recommendations;
                this.totalCount = page.count;
                this.resetSelection();
                this.loading = false;
            },
            error: (err) => {
                console.error(err);
                this.notificationService.showError('Erreur chargement recommandations');
                this.loading = false;
            }
        });
    }

    loadAllRecommendationsForStats(): void {
        this.recoService.getRecommendations({ limit: 5000 }).subscribe({
            next: (page) => {
                this.allRecommendations = page.recommendations;
                this.totalRecos = page.count;
                this.approvedCount = this.allRecommendations.filter(r => r.status === 'APPROVED').length;
                this.rejectedCount = this.allRecommendations.filter(r => r.status === 'REJECTED').length;
                this.pendingCount = this.allRecommendations.filter(r => r.status === 'PENDING').length;

                const totalScored = this.allRecommendations.filter(r => r.score != null);
                if (totalScored.length) {
                    this.avgScore = totalScored.reduce((s, r) => s + r.score, 0) / totalScored.length;
                }
                this.approvalRate = this.totalRecos ? Math.round((this.approvedCount / this.totalRecos) * 100) : 0;

                this.buildProfileDonut();
                this.computeAlerts();
            },
            error: (err) => console.error('Erreur chargement stats globales', err)
        });
    }

    // Donut chart helpers
    private buildProfileDonut(): void {
        const profileCount = new Map<string, number>();
        this.allRecommendations.forEach(reco => {
            const profile = reco.profileName;
            if (profile) {
                profileCount.set(profile, (profileCount.get(profile) || 0) + 1);
            }
        });

        const total = Array.from(profileCount.values()).reduce((s, v) => s + v, 0);
        this.profileLabels = Array.from(profileCount.keys());
        this.profileCounts = this.profileLabels.map(l => profileCount.get(l)!);
        this.profilePercents = this.profileCounts.map(c => total > 0 ? Math.round((c / total) * 100) : 0);
        this.profileArcs = this.buildDonutArcs(this.profileCounts.map(c => total > 0 ? (c / total) * 100 : 0));
    }

    private buildDonutArcs(percents: number[]): { path: string; color: string }[] {
        const arcs: { path: string; color: string }[] = [];
        let current = 0;
        percents.forEach((pct, i) => {
            const start = current;
            const end = current + pct * 3.6;
            arcs.push({
                path: this.describeArc(150, 150, 110, start, end),
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

    private describeArc(cx: number, cy: number, r: number, startDeg: number, endDeg: number): string {
        if (Math.abs(endDeg - startDeg) >= 360) endDeg = startDeg + 359.99;
        if (Math.abs(endDeg - startDeg) < 0.1) return '';
        const s = this.polarToCartesian(cx, cy, r, startDeg);
        const e = this.polarToCartesian(cx, cy, r, endDeg);
        const lg = endDeg - startDeg > 180 ? 1 : 0;
        return `M ${s.x} ${s.y} A ${r} ${r} 0 ${lg} 1 ${e.x} ${e.y}`;
    }

    // Alerts
    private computeAlerts(): void {
        this.alertMessages = [];
        const now = new Date();

        // Recommendations en attente depuis plus de 7 jours
        this.allRecommendations.filter(r => r.status === 'PENDING').forEach(r => {
            const daysPending = Math.floor((now.getTime() - new Date(r.generatedAt).getTime()) / (1000 * 3600 * 24));
            if (daysPending > 7) {
                this.alertMessages.push({
                    severity: 'warning',
                    message: `Reco ${r.id} (${r.profileName}) en attente depuis ${daysPending} jours`
                });
            }
        });

        // Score très bas (<0.3) parmi les actives
        this.allRecommendations.filter(r => r.status !== 'REJECTED' && r.score < 0.3).forEach(r => {
            this.alertMessages.push({
                severity: 'critical',
                message: `Reco ${r.id} (${r.offerTitle}) a un score très bas (${r.score.toFixed(2)})`
            });
        });

        // Limite d'affichage
        if (this.alertMessages.length > 15) {
            const total = this.alertMessages.length;
            this.alertMessages = this.alertMessages.slice(0, 15);
            this.alertMessages.push({
                severity: 'warning',
                message: `... et ${total - 15} autres alertes non affichées`
            });
        }
    }

    // Batch & selection
    onSelectionChange(): void {
        this.selectedItems.clear();
        this.recommendations.forEach(r => { if (r.selected) this.selectedItems.add(r.id); });
        this.isAllSelected = this.selectedItems.size === this.recommendations.length;
    }

    toggleSelectAll(event: any): void {
        if (event.target.checked) {
            this.recommendations.forEach(r => r.selected = true);
            this.selectedItems = new Set(this.recommendations.map(r => r.id));
        } else {
            this.recommendations.forEach(r => r.selected = false);
            this.selectedItems.clear();
        }
        this.isAllSelected = event.target.checked;
    }

    bulkApproveSelected(): void {
        if (this.selectedItems.size === 0) return;
        if (confirm(`Approuver ${this.selectedItems.size} recommandation(s) sélectionnée(s) ?`)) {
            const requests = Array.from(this.selectedItems).map(id => this.recoService.approveRecommendation(id).toPromise());
            Promise.all(requests).then(() => {
                this.notificationService.showSuccess(`${this.selectedItems.size} recommandation(s) approuvée(s)`);
                this.loadRecommendations();
                this.loadAllRecommendationsForStats();
            }).catch(() => this.notificationService.showError('Erreur lors de l’approbation batch'));
        }
    }

    bulkRejectSelected(): void {
        if (this.selectedItems.size === 0) return;
        if (confirm(`Rejeter ${this.selectedItems.size} recommandation(s) sélectionnée(s) ?`)) {
            const requests = Array.from(this.selectedItems).map(id => this.recoService.rejectRecommendation(id).toPromise());
            Promise.all(requests).then(() => {
                this.notificationService.showSuccess(`${this.selectedItems.size} recommandation(s) rejetée(s)`);
                this.loadRecommendations();
                this.loadAllRecommendationsForStats();
            }).catch(() => this.notificationService.showError('Erreur lors du rejet batch'));
        }
    }

    bulkApproveByProfile(): void {
        const profile = this.filterForm.get('profile')?.value;
        if (!profile) { this.notificationService.showError('Veuillez sélectionner un profil'); return; }
        if (confirm(`Approuver toutes les recommandations en attente du profil "${profile}" ?`)) {
            this.recoService.bulkApprove(profile).subscribe({
                next: (result) => {
                    this.notificationService.showSuccess(result.message);
                    this.loadRecommendations();
                    this.loadAllRecommendationsForStats();
                },
                error: () => this.notificationService.showError('Erreur approbation masse')
            });
        }
    }

    approveSingle(id: number): void {
        this.recoService.approveRecommendation(id).subscribe({
            next: () => {
                this.notificationService.showSuccess('Recommandation approuvée');
                this.loadRecommendations();
                this.loadAllRecommendationsForStats();
            },
            error: () => this.notificationService.showError('Erreur approbation')
        });
    }

    rejectSingle(id: number): void {
        this.recoService.rejectRecommendation(id).subscribe({
            next: () => {
                this.notificationService.showSuccess('Recommandation rejetée');
                this.loadRecommendations();
                this.loadAllRecommendationsForStats();
            },
            error: () => this.notificationService.showError('Erreur rejet')
        });
    }

    /**
     * ★ NOUVEAU — Diffuse une recommandation APPROUVÉE à tout le profil
     * via le topic FCM `profile_{clusterId}` (1 publication = N destinataires).
     * Seulement disponible pour les recos APPROVED.
     */
    sendToProfileSingle(reco: RecommendationResponse): void {
        if (reco.status !== 'APPROVED') {
            this.notificationService.showInfo(
                'Seules les recommandations APPROUVÉES peuvent être diffusées au profil.'
            );
            return;
        }
        this.recoService.sendToProfile(reco.id, reco.clusterId ?? undefined).subscribe({
            next: (result) => {
                if (result?.status === 'SENT') {
                    this.notificationService.showSuccess(
                        `Offre diffusée au profil ${result.clusterId} ` +
                        `(topic=${result.topic}, ~${result.estimatedRecipients ?? 0} destinataires)`
                    );
                    this.loadRecommendations();
                } else {
                    this.notificationService.showError(
                        `Diffusion échouée : ${result?.message ?? 'erreur inconnue'}`
                    );
                }
            },
            error: () => this.notificationService.showError('Erreur diffusion au profil')
        });
    }

    regenerateRecommendations(): void {
        if (this.regenerating) return;
        this.regenerating = true;
        this.notificationService.showInfo('Génération des recommandations en cours...');
        this.metricsService.regenerateRecommendations().subscribe({
            next: () => {
                this.notificationService.showSuccess('Génération des recommandations lancée. Mise à jour dans quelques instants.');
                setTimeout(() => {
                    this.loadRecommendations();
                    this.loadAllRecommendationsForStats();
                    this.regenerating = false;
                }, 3000);
            },
            error: (err) => {
                console.error(err);
                this.notificationService.showError('Erreur lors de la régénération des recommandations');
                this.regenerating = false;
            }
        });
    }

    onFilterChange(): void {
        this.currentPage = 0;
        this.loadRecommendations();
    }

    onPageChange(page: number): void {
        this.currentPage = page;
        this.loadRecommendations();
    }

    onPageSizeChange(size: number): void {
        this.pageSize = size;
        this.currentPage = 0;
        this.loadRecommendations();
    }

    get paginatedRecommendations(): RecommendationResponse[] {
        return this.recommendations;
    }

    get totalPages(): number {
        return Math.ceil(this.totalCount / this.pageSize);
    }

    viewDetails(reco: RecommendationResponse): void {
        this.router.navigate(['/layout/recommendation/recommendations', reco.id]);
    }

    private resetSelection(): void {
        this.selectedItems.clear();
        this.isAllSelected = false;
        this.recommendations.forEach(r => r.selected = false);
    }
}