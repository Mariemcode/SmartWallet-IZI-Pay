import { Component, OnInit } from '@angular/core';
import { catchError, of } from 'rxjs';
import { IaAdminService } from '../../services/ia-admin/ia-admin.service';

@Component({
    selector: 'app-alerts',
    templateUrl: './alerts.component.html',
    styleUrl: './alerts.component.css',
})
export class AlertsComponent implements OnInit {

    readonly Math = Math;

    alertsList: any[] = [];
    alertsPage = 0;
    alertsTotal = 0;
    alertsLoading = false;
    filterSeverity: '' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' = '';
    readonly pageSize = 20;

    // ★ Stats agrégées
    countBySeverity: Record<string, number> = {};
    countByType: Record<string, number> = {};

    constructor(private svc: IaAdminService) {}

    ngOnInit(): void {
        this.loadAlerts();
    }

    loadAlerts(): void {
        this.alertsLoading = true;
        this.svc.getAlerts(this.alertsPage, this.pageSize, this.filterSeverity || undefined)
            .pipe(catchError(() => of({ content: [], totalElements: 0, data: [], total: 0 })))
            .subscribe((r: any) => {
                // ★ FIX : FastAPI renvoie { content, totalElements } (style Page Spring)
                //         mais Spring AdminController forward parfois sous { data, total } selon les sources
                this.alertsList  = r.content || r.data || [];
                this.alertsTotal = r.totalElements ?? r.total ?? 0;
                this.alertsLoading = false;

                // Agrégation rapide pour les KPIs
                this.countBySeverity = {};
                this.countByType = {};
                for (const a of this.alertsList) {
                    const sev = (a.severity || 'UNKNOWN').toUpperCase();
                    this.countBySeverity[sev] = (this.countBySeverity[sev] || 0) + 1;
                    const t = a.alert_type || a.type || 'UNKNOWN';
                    this.countByType[t] = (this.countByType[t] || 0) + 1;
                }
            });
    }

    filter(): void {
        this.alertsPage = 0;
        this.loadAlerts();
    }

    next(): void { this.alertsPage++; this.loadAlerts(); }
    prev(): void {
        if (this.alertsPage > 0) { this.alertsPage--; this.loadAlerts(); }
    }

    severityClass(s: string): string {
        return 'sev-' + (s || 'unknown').toLowerCase();
    }

    severityIcon(s: string): string {
        return ({
            CRITICAL: 'bi-exclamation-octagon-fill',
            HIGH:     'bi-exclamation-triangle-fill',
            MEDIUM:   'bi-exclamation-circle',
            LOW:      'bi-info-circle',
        } as any)[s] || 'bi-question-circle';
    }

    severityColor(s: string): string {
        return ({
            CRITICAL: '#c62828',
            HIGH:     '#e65100',
            MEDIUM:   '#1565c0',
            LOW:      '#6b7fa3',
        } as any)[s] || '#6b7fa3';
    }

    fmtDate(iso: string | null): string {
        if (!iso) return '—';
        try {
            return new Date(iso.replace(' ', 'T')).toLocaleString('fr-FR', {
                day: '2-digit', month: '2-digit', year: 'numeric',
                hour: '2-digit', minute: '2-digit',
            });
        } catch { return iso ?? '—'; }
    }

    /** Tronque proprement l'id client (UUID ou non) */
    shortId(id: string | null): string {
        if (!id) return '—';
        return id.length > 10 ? id.substring(0, 10) + '…' : id;
    }
}