import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, takeUntil, catchError, of, finalize, timer, switchMap } from 'rxjs';
import { HealthDTO, KpiSummaryDTO, DriftStatusDTO, MonitoringAlertDTO, MigrationSummaryDTO, RetrainStatusDTO } from '../../models/profile.model';
import { ProfileService } from '../../services/profile/profile.service';
import { NotificationService } from '../../services/recommendation/notif/notification.service';

@Component({
    selector: 'app-profilemodel',
    templateUrl: './profilemodel.component.html',
    styleUrl: './profilemodel.component.css'
})
export class ProfilemodelComponent implements OnInit, OnDestroy {
    private destroy$ = new Subject<void>();

    loadingHealth = false;
    loadingKpi = false;
    loadingDrift = false;
    loadingAlerts = false;
    loadingMigrations = false;
    retrainLoading = false;

    health: HealthDTO | null = null;
    kpiList: KpiSummaryDTO[] = [];
    driftRuns: DriftStatusDTO[] = [];
    alerts: MonitoringAlertDTO[] = [];
    migrations: MigrationSummaryDTO[] = [];
    retrainStatus: RetrainStatusDTO | null = null;
    private retrainPollingSubscription: any = null;

    private colors: string[] = [
        '#1565c0', '#c62828', '#2e7d32', '#6a1b9a', '#e65100',
        '#00838f', '#4527a0', '#558b2f', '#ad1457', '#00695c',
        '#f9a825', '#0277bd', '#4e342e', '#37474f', '#6d4c41',
    ];

    constructor(
        private profileService: ProfileService,
        private notificationService: NotificationService
    ) {}

    ngOnInit(): void {
        console.log('🚀 ProfilemodelComponent INIT');
        this.loadAllData();
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
        if (this.retrainPollingSubscription) {
            this.retrainPollingSubscription.unsubscribe();
        }
    }

    loadAllData(): void {
        console.log('📥 Chargement de toutes les données...');
        this.loadHealth();
        this.loadKpi();
        this.loadDriftStatus();
        this.loadAlerts();
        this.loadMigrations();
        this.loadRetrainStatus();
    }

    loadHealth(): void {
        this.loadingHealth = true;
        console.log('🔍 GET Health...');
        this.profileService
            .getHealth()
            .pipe(
                takeUntil(this.destroy$),
                catchError((err) => {
                    console.error('❌ Erreur health:', err);
                    return of(null);
                }),
                finalize(() => (this.loadingHealth = false))
            )
            .subscribe((response) => {
                console.log('✅ Health response:', response);
                if (response?.success) this.health = response.data;
            });
    }

    loadKpi(): void {
        this.loadingKpi = true;
        console.log('🔍 GET KPI...');
        this.profileService
            .getKpiSummary()
            .pipe(
                takeUntil(this.destroy$),
                catchError((err) => {
                    console.error('❌ Erreur KPI:', err);
                    return of(null);
                }),
                finalize(() => (this.loadingKpi = false))
            )
            .subscribe((response) => {
                console.log('✅ KPI response:', response);
                if (response?.success) this.kpiList = response.data;
            });
    }

    loadDriftStatus(): void {
        this.loadingDrift = true;
        console.log('🔍 GET Drift...');
        this.profileService
            .getDriftStatus()
            .pipe(
                takeUntil(this.destroy$),
                catchError((err) => {
                    console.error('❌ Erreur drift:', err);
                    return of(null);
                }),
                finalize(() => (this.loadingDrift = false))
            )
            .subscribe((response) => {
                console.log('✅ Drift response:', response);
                if (response?.success) this.driftRuns = response.data;
            });
    }

    loadAlerts(): void {
        this.loadingAlerts = true;
        console.log('🔍 GET Alerts...');
        this.profileService
            .getMonitoringAlerts()
            .pipe(
                takeUntil(this.destroy$),
                catchError((err) => {
                    console.error('❌ Erreur alertes:', err);
                    return of(null);
                }),
                finalize(() => (this.loadingAlerts = false))
            )
            .subscribe((response) => {
                console.log('✅ Alerts response:', response);
                if (response?.success) this.alerts = response.data;
            });
    }

    loadMigrations(): void {
        this.loadingMigrations = true;
        console.log('🔍 GET Migrations...');
        this.profileService
            .getMigrationsSummary()
            .pipe(
                takeUntil(this.destroy$),
                catchError((err) => {
                    console.error('❌ Erreur migrations:', err);
                    return of(null);
                }),
                finalize(() => (this.loadingMigrations = false))
            )
            .subscribe((response) => {
                console.log('✅ Migrations response:', response);
                if (response?.success) this.migrations = response.data;
            });
    }

    loadRetrainStatus(): void {
        console.log('🔍 GET Retrain Status...');
        this.profileService
            .getRetrainStatus()
            .pipe(
                takeUntil(this.destroy$),
                catchError((err) => {
                    console.error('❌ Erreur statut retrain:', {
                        status: err?.status,
                        statusText: err?.statusText,
                        message: err?.message,
                        url: err?.url,
                        error: err?.error
                    });
                    return of(null);
                })
            )
            .subscribe((response) => {
                console.log('✅ Retrain Status response:', response);
                if (response?.success) {
                    this.retrainStatus = response.data;
                } else if (response) {
                    // Si la réponse n'a pas de structure ApiResponse
                    console.log('⚠️ Retrain status format différent:', response);
                    this.retrainStatus = response as any;
                }
            });
    }

    // ═══════════════════════════════════════════════════════════
    // 🔄 TRIGGER RETRAIN - AVEC LOGS DÉTAILLÉS
    // ═══════════════════════════════════════════════════════════
    triggerRetrain(): void {
        console.log('══════════════════════════════════════════════');
        console.log('🔄 BOUTON RETRAIN CLIQUÉ');
        console.log('══════════════════════════════════════════════');

        if (this.retrainLoading) {
            console.log('⚠️ Retrain déjà en cours, ignoré');
            return;
        }

        this.retrainLoading = true;

        console.log('📤 Appel de profileService.triggerRetrain("admin")');
        console.log('📍 URL complète :', 'http://localhost:8222/api/v1/classification/admin/retrain');
        console.log('📦 Payload :', { admin_user: 'admin' });

        this.notificationService.showInfo('Déclenchement du ré-entraînement...');

        this.profileService
            .triggerRetrain('admin')
            .pipe(
                takeUntil(this.destroy$),
                catchError((err) => {
                    console.log('══════════════════════════════════════════════');
                    console.error('❌❌❌ ERREUR RETRAIN ❌❌❌');
                    console.log('══════════════════════════════════════════════');
                    console.error('📋 DÉTAILS COMPLETS DE L\'ERREUR :');
                    console.error('  1. Status HTTP :', err?.status);
                    console.error('  2. Status Text :', err?.statusText);
                    console.error('  3. Message :', err?.message);
                    console.error('  4. URL appelée :', err?.url);
                    console.error('  5. Nom de l\'erreur :', err?.name);

                    if (err?.error) {
                        console.error('  6. Corps de l\'erreur (err.error) :');
                        console.error('     Type :', typeof err.error);
                        console.error('     Contenu :', JSON.stringify(err.error, null, 4));

                        if (typeof err.error === 'object') {
                            console.error('     - detail :', err.error.detail);
                            console.error('     - message :', err.error.message);
                            console.error('     - error :', err.error.error);
                            console.error('     - status :', err.error.status);
                        }
                    }

                    if (err?.headers) {
                        console.error('  7. Headers de réponse :');
                        err.headers.keys().forEach((key: string) => {
                            console.error(`     ${key}: ${err.headers.get(key)}`);
                        });
                    }

                    console.error('  8. Objet erreur complet :', err);
                    console.log('══════════════════════════════════════════════');

                    this.notificationService.showError('Échec du déclenchement : ' + (err?.error?.detail || err?.message || 'Erreur inconnue'));
                    this.retrainLoading = false;
                    return of(null);
                })
            )
            .subscribe((response) => {
                console.log('══════════════════════════════════════════════');
                console.log('✅ RÉPONSE RETRAIN REÇUE');
                console.log('══════════════════════════════════════════════');
                console.log('📋 Réponse complète :', JSON.stringify(response, null, 2));
                console.log('  - success :', response?.success);
                console.log('  - message :', response?.message);
                console.log('  - data :', response?.data);
                console.log('══════════════════════════════════════════════');

                if (response?.success) {
                    console.log('✅ Retrain accepté, démarrage du polling...');
                    this.notificationService.showSuccess('Ré-entraînement déclenché avec succès. Surveillance en cours...');
                    this.pollRetrainCompletion();
                } else {
                    console.log('⚠️ Retrain non accepté :', response?.message);
                    this.notificationService.showError('Erreur : ' + (response?.message || 'Réponse inattendue'));
                    this.retrainLoading = false;
                }
            });
    }

    private pollRetrainCompletion(): void {
        console.log('🔄 Démarrage du polling (toutes les 3 secondes)...');

        if (this.retrainPollingSubscription) {
            this.retrainPollingSubscription.unsubscribe();
        }

        let pollCount = 0;

        this.retrainPollingSubscription = timer(0, 3000)
            .pipe(
                takeUntil(this.destroy$),
                switchMap(() => {
                    pollCount++;
                    console.log(`📊 Poll #${pollCount} - Vérification statut retrain...`);
                    return this.profileService.getRetrainStatus();
                }),
                catchError((err) => {
                    console.error(`❌ Erreur polling #${pollCount}:`, err);
                    return of(null);
                })
            )
            .subscribe((statusResp) => {
                console.log(`📊 Poll #${pollCount} - Réponse :`, statusResp);

                if (statusResp?.success) {
                    this.retrainStatus = statusResp.data;
                    console.log('  - running :', this.retrainStatus?.running);
                    console.log('  - last_result :', this.retrainStatus?.last_result);

                    if (this.retrainStatus && !this.retrainStatus.running) {
                        console.log('🏁 Retrain terminé !');
                        this.retrainLoading = false;

                        if (this.retrainStatus.last_result === 'success') {
                            console.log('✅ Retrain SUCCÈS');
                            this.notificationService.showSuccess('Ré-entraînement terminé avec succès !');
                        } else {
                            console.error('❌ Retrain ÉCHEC :', this.retrainStatus.last_result);
                            this.notificationService.showError(
                                `Ré-entraînement terminé avec erreur : ${this.retrainStatus.last_result || 'inconnue'}`
                            );
                        }

                        if (this.retrainPollingSubscription) {
                            this.retrainPollingSubscription.unsubscribe();
                            this.retrainPollingSubscription = null;
                        }

                        // Recharger les données après ré-entraînement
                        console.log('🔄 Rechargement des données...');
                        this.loadDriftStatus();
                        this.loadKpi();
                        this.loadHealth();
                    } else {
                        console.log('⏳ Retrain toujours en cours...');
                    }
                } else {
                    console.log('⚠️ Réponse polling sans success :', statusResp);
                    if (statusResp) {
                        this.retrainStatus = statusResp as any;
                        console.log('  - running :', this.retrainStatus?.running);
                        console.log('  - last_result :', this.retrainStatus?.last_result);
                    }
                }
            });
    }

    // ============ MÉTHODES NULL-SAFE ============

    formatDate(dateStr?: string | null): string {
        if (!dateStr) return '—';
        try {
            return new Date(dateStr).toLocaleString('fr-FR', {
                day: '2-digit',
                month: 'short',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch {
            return '—';
        }
    }

    getColor(index?: number | null): string {
        if (index == null || index < 0) return '#cccccc';
        return this.colors[index % this.colors.length];
    }

    getPsiCardClass(status?: string | null): string {
        if (!status) return 'kpi-card kpi-gray';
        switch (status.toLowerCase()) {
            case 'drift':   return 'kpi-card kpi-danger';
            case 'warning': return 'kpi-card kpi-warning';
            case 'ok':      return 'kpi-card kpi-teal';
            default:        return 'kpi-card kpi-gray';
        }
    }

    getPsiBadgeClass(status?: string | null): string {
        if (!status) return 'chip-muted';
        switch (status.toLowerCase()) {
            case 'drift':   return 'chip-danger';
            case 'warning': return 'chip-warning';
            case 'ok':      return 'chip-success';
            default:        return 'chip-muted';
        }
    }

    getSeverityBadgeClass(severity?: string | null): string {
        if (!severity) return 'chip-muted';
        switch (severity.toUpperCase()) {
            case 'CRITICAL': return 'chip-danger';
            case 'HIGH':     return 'chip-warning';
            case 'MEDIUM':   return 'chip-info';
            case 'LOW':      return 'chip-success';
            default:         return 'chip-muted';
        }
    }

    getChurnClass(v?: number | null): string {
        if (v == null) return 'metric-val churn-unknown';
        if (v > 0.6) return 'metric-val churn-high';
        if (v > 0.3) return 'metric-val churn-med';
        return 'metric-val churn-low';
    }

    getRiskClass(v?: number | null): string {
        if (v == null) return 'score-pill score-unknown';
        if (v > 0.6) return 'score-pill score-high';
        if (v > 0.3) return 'score-pill score-med';
        return 'score-pill score-low';
    }
}