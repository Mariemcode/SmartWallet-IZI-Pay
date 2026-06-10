import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription, catchError, of, interval } from 'rxjs';
import { IaAdminService } from '../../services/ia-admin/ia-admin.service';

interface NotifRow {
    id?: string | number;
    client_id?: string;
    titre?: string;
    message?: string;
    type?: string;
    envoye_par?: string;       // 'ADMIN' = manuelle, sinon système
    statut?: string;           // ENVOYE / ECHEC / PROGRAMME
    date_envoi?: string;
    date_planifiee?: string;
}

// Types de notifs admin manuelles (depuis le formulaire)
const MANUAL_TYPES = ['ATTENTION', 'INFO', 'PROMO', 'URGENT'];

// Types de notifs système (générées par les schedulers)
const SYSTEM_TYPES = ['OFFRE', 'OFFRE_PROFIL', 'BILL_REMINDER', 'BALANCE_ALERT',
    'WEEKLY_SUMMARY', 'OCR_REMINDER', 'CRITIQUE'];

@Component({
    selector: 'app-notifications',
    templateUrl: './notifications.component.html',
    styleUrl: './notifications.component.css',
})
export class NotificationsComponent implements OnInit, OnDestroy {

    // Exposer Math au template pour Math.ceil(currentTabTotal / histPageSize)
    readonly Math = Math;

    // ────────────────────────────────────────────────────────────
    //  Onglet actif
    // ────────────────────────────────────────────────────────────
    activeTab: 'compose' | 'critical' | 'manual' | 'system' = 'compose';

    // ────────────────────────────────────────────────────────────
    //  Formulaire d'envoi
    // ────────────────────────────────────────────────────────────
    nfCid = '';
    nfTitre = '';
    nfMsg = '';
    nfType: 'ATTENTION' | 'INFO' | 'PROMO' | 'URGENT' = 'INFO';
    nfAll = false;
    nfScheduleDate = '';

    nfResult = '';
    nfSending = false;
    isError = false;

    // ────────────────────────────────────────────────────────────
    //  Historique brut + listes filtrées
    // ────────────────────────────────────────────────────────────
    allNotifs: NotifRow[] = [];

    // Filtres par onglet
    filterCriticalSeverity: 'all' | 'URGENT' | 'CRITIQUE' = 'all';
    filterManualType: 'all' | 'ATTENTION' | 'INFO' | 'PROMO' | 'URGENT' = 'all';
    filterSystemType: 'all' | 'OFFRE' | 'BILL_REMINDER' | 'BALANCE_ALERT' | 'WEEKLY_SUMMARY' | 'OCR_REMINDER' = 'all';
    searchQuery = '';

    // Pagination par onglet
    histPage = 0;
    histPageSize = 20;
    histTotal = 0;
    loadingHistory = false;

    // ────────────────────────────────────────────────────────────
    //  KPIs (calculés depuis allNotifs)
    // ────────────────────────────────────────────────────────────
    kpiTotalToday = 0;
    kpiCriticalWeek = 0;
    kpiFailed = 0;
    kpiScheduled = 0;
    kpiManualCount = 0;
    kpiSystemCount = 0;

    // Auto-refresh
    private autoRefreshSub?: Subscription;
    autoRefresh = false;

    constructor(private svc: IaAdminService) {}

    ngOnInit(): void {
        this.loadHistory();
    }

    ngOnDestroy(): void {
        this.autoRefreshSub?.unsubscribe();
    }

    // ════════════════════════════════════════════════════════════
    //  Onglets
    // ════════════════════════════════════════════════════════════
    setTab(t: 'compose' | 'critical' | 'manual' | 'system') {
        this.activeTab = t;
        this.histPage = 0;
    }

    toggleAutoRefresh() {
        this.autoRefresh = !this.autoRefresh;
        if (this.autoRefresh) {
            this.autoRefreshSub = interval(15000).subscribe(() => this.loadHistory());
        } else {
            this.autoRefreshSub?.unsubscribe();
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Envoi / Planification
    // ════════════════════════════════════════════════════════════
    send(): void {
        if (!this.nfTitre.trim() || !this.nfMsg.trim()) {
            this.nfResult = 'Titre et message sont obligatoires';
            this.isError = true;
            return;
        }
        if (!this.nfAll && !this.nfCid.trim()) {
            this.nfResult = 'Client ID requis ou cochez "Tous les clients"';
            this.isError = true;
            return;
        }

        this.nfSending = true;
        this.nfResult = '';
        this.isError = false;

        this.svc.sendNotif({
            client_id: this.nfAll ? 'ALL' : this.nfCid,
            titre: this.nfTitre,
            message: this.nfMsg,
            type: this.nfType,
        })
            .pipe(catchError(e => of({ error: e?.error?.error || 'Erreur réseau' })))
            .subscribe((r: any) => {
                this.nfSending = false;
                if (r.error) {
                    this.nfResult = '❌ ' + r.error;
                    this.isError = true;
                } else {
                    this.nfResult = `✓ ${r.sent ?? 1} notification(s) envoyée(s)`;
                    this.isError = false;
                    this.resetForm();
                    this.loadHistory();
                }
            });
    }

    schedule(): void {
        if (!this.nfScheduleDate) {
            this.nfResult = '❌ Date de planification requise';
            this.isError = true;
            return;
        }
        if (!this.nfTitre.trim() || !this.nfMsg.trim()) {
            this.nfResult = 'Titre et message sont obligatoires';
            this.isError = true;
            return;
        }

        this.nfSending = true;
        this.svc.scheduleNotif({
            client_id: this.nfAll ? 'ALL' : this.nfCid,
            titre: this.nfTitre,
            message: this.nfMsg,
            type: this.nfType,
            date_planifiee: this.nfScheduleDate,
        })
            .pipe(catchError(e => of({ error: e?.error?.error || 'Erreur' })))
            .subscribe((r: any) => {
                this.nfSending = false;
                if (r.error) {
                    this.nfResult = '❌ ' + r.error;
                    this.isError = true;
                } else {
                    this.nfResult = '✓ Notification planifiée pour ' + this.fmtDate(this.nfScheduleDate);
                    this.isError = false;
                    this.resetForm();
                    this.loadHistory();
                }
            });
    }

    resetForm(): void {
        this.nfTitre = '';
        this.nfMsg = '';
        this.nfScheduleDate = '';
        // garde nfCid, nfAll, nfType pour permettre l'envoi multiple rapide
    }

    // ════════════════════════════════════════════════════════════
    //  Chargement + filtrage
    // ════════════════════════════════════════════════════════════
    loadHistory(): void {
        this.loadingHistory = true;
        // On charge un gros lot pour pouvoir filtrer côté front
        this.svc.notifHistory(0, 200)
            .pipe(catchError(() => of({ data: [], content: [], total: 0 })))
            .subscribe((r: any) => {
                this.allNotifs = r.data || r.content || [];
                this.histTotal = r.total || r.totalElements || this.allNotifs.length;
                this.loadingHistory = false;
                this.computeKpis();
            });
    }

    computeKpis(): void {
        const now = new Date();
        const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        const startOfWeek = new Date(now); startOfWeek.setDate(now.getDate() - 7);

        this.kpiTotalToday = 0;
        this.kpiCriticalWeek = 0;
        this.kpiFailed = 0;
        this.kpiScheduled = 0;
        this.kpiManualCount = 0;
        this.kpiSystemCount = 0;

        for (const n of this.allNotifs) {
            const d = this.parseDate(n.date_envoi || n.date_planifiee || null);
            const type = (n.type || '').toUpperCase();
            const statut = (n.statut || '').toUpperCase();

            if (d && d >= startOfToday) this.kpiTotalToday++;
            if (d && d >= startOfWeek && (type === 'URGENT' || type === 'CRITIQUE')) this.kpiCriticalWeek++;
            if (statut === 'ECHEC' || statut === 'FAILED') this.kpiFailed++;
            if (statut === 'PROGRAMME' || statut === 'SCHEDULED') this.kpiScheduled++;

            if (this.isManual(n)) this.kpiManualCount++;
            else if (this.isSystem(n)) this.kpiSystemCount++;
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Classification : manuelle vs système
    // ────────────────────────────────────────────────────────────
    isManual(n: NotifRow): boolean {
        if (n.envoye_par === 'ADMIN') return true;
        const type = (n.type || '').toUpperCase();
        return MANUAL_TYPES.includes(type);
    }

    isSystem(n: NotifRow): boolean {
        if (n.envoye_par && n.envoye_par !== 'ADMIN') return true;
        const type = (n.type || '').toUpperCase();
        return SYSTEM_TYPES.includes(type);
    }

    isCritical(n: NotifRow): boolean {
        const type = (n.type || '').toUpperCase();
        return type === 'URGENT' || type === 'CRITIQUE' || type === 'CRITICAL';
    }

    // ────────────────────────────────────────────────────────────
    //  Listes filtrées par onglet (paginées)
    // ────────────────────────────────────────────────────────────
    get filteredCritical(): NotifRow[] {
        let list = this.allNotifs.filter(n => this.isCritical(n));
        if (this.filterCriticalSeverity !== 'all') {
            list = list.filter(n => (n.type || '').toUpperCase() === this.filterCriticalSeverity);
        }
        return this.applySearchAndPaginate(list);
    }

    get filteredManual(): NotifRow[] {
        let list = this.allNotifs.filter(n => this.isManual(n));
        if (this.filterManualType !== 'all') {
            list = list.filter(n => (n.type || '').toUpperCase() === this.filterManualType);
        }
        return this.applySearchAndPaginate(list);
    }

    get filteredSystem(): NotifRow[] {
        let list = this.allNotifs.filter(n => this.isSystem(n) && !this.isCritical(n));
        if (this.filterSystemType !== 'all') {
            list = list.filter(n => (n.type || '').toUpperCase() === this.filterSystemType);
        }
        return this.applySearchAndPaginate(list);
    }

    get currentTabTotal(): number {
        if (this.activeTab === 'critical') return this.allNotifs.filter(n => this.isCritical(n)).length;
        if (this.activeTab === 'manual')   return this.allNotifs.filter(n => this.isManual(n)).length;
        if (this.activeTab === 'system')   return this.allNotifs.filter(n => this.isSystem(n) && !this.isCritical(n)).length;
        return 0;
    }

    private applySearchAndPaginate(list: NotifRow[]): NotifRow[] {
        // Trier par date desc
        list = [...list].sort((a, b) => {
            const da = this.parseDate(a.date_envoi || a.date_planifiee || null);
            const db = this.parseDate(b.date_envoi || b.date_planifiee || null);
            return (db?.getTime() ?? 0) - (da?.getTime() ?? 0);
        });
        // Recherche full-text basique
        if (this.searchQuery.trim()) {
            const q = this.searchQuery.toLowerCase();
            list = list.filter(n =>
                (n.titre || '').toLowerCase().includes(q) ||
                (n.message || '').toLowerCase().includes(q) ||
                (n.client_id || '').toLowerCase().includes(q)
            );
        }
        // Pagination
        const start = this.histPage * this.histPageSize;
        return list.slice(start, start + this.histPageSize);
    }

    nextPage(): void { this.histPage++; }
    prevPage(): void { if (this.histPage > 0) this.histPage--; }

    // ════════════════════════════════════════════════════════════
    //  Helpers UI
    // ════════════════════════════════════════════════════════════

    /** Couleur thématique pour chaque type (palette recommendationmodel) */
    typeColor(type: string | undefined): string {
        const t = (type || '').toUpperCase();
        switch (t) {
            case 'URGENT':
            case 'CRITIQUE':
            case 'CRITICAL':      return '#c62828';
            case 'ATTENTION':     return '#e65100';
            case 'INFO':          return '#1565c0';
            case 'PROMO':
            case 'OFFRE':
            case 'OFFRE_PROFIL':  return '#6a1b9a';
            case 'BILL_REMINDER': return '#2e7d32';
            case 'BALANCE_ALERT': return '#e65100';
            case 'WEEKLY_SUMMARY':return '#6b7fa3';
            case 'OCR_REMINDER':  return '#00838f';
            default:              return '#94a3b8';
        }
    }

    typeIcon(type: string | undefined): string {
        const t = (type || '').toUpperCase();
        switch (t) {
            case 'URGENT':
            case 'CRITIQUE':
            case 'CRITICAL':      return 'bi-exclamation-octagon-fill';
            case 'ATTENTION':     return 'bi-exclamation-triangle-fill';
            case 'INFO':          return 'bi-info-circle-fill';
            case 'PROMO':
            case 'OFFRE':
            case 'OFFRE_PROFIL':  return 'bi-gift-fill';
            case 'BILL_REMINDER': return 'bi-receipt';
            case 'BALANCE_ALERT': return 'bi-wallet2';
            case 'WEEKLY_SUMMARY':return 'bi-bar-chart';
            case 'OCR_REMINDER':  return 'bi-camera';
            default:              return 'bi-bell';
        }
    }

    /** Libellé lisible pour le badge type */
    typeLabel(type: string | undefined): string {
        const t = (type || '').toUpperCase();
        const map: Record<string, string> = {
            'URGENT':         'Urgent',
            'CRITIQUE':       'Critique',
            'CRITICAL':       'Critique',
            'ATTENTION':      'Attention',
            'INFO':           'Info',
            'PROMO':          'Promo',
            'OFFRE':          'Offre',
            'OFFRE_PROFIL':   'Offre profil',
            'BILL_REMINDER':  'Facture',
            'BALANCE_ALERT':  'Solde',
            'WEEKLY_SUMMARY': 'Bilan hebdo',
            'OCR_REMINDER':   'Scan OCR',
        };
        return map[t] || t || 'Inconnu';
    }

    statusColor(s: string | undefined): string {
        const v = (s || '').toUpperCase();
        if (v === 'ENVOYE' || v === 'SENT' || v === 'OK')      return '#2e7d32';
        if (v === 'ECHEC'  || v === 'FAILED' || v === 'ERROR') return '#c62828';
        if (v === 'PROGRAMME' || v === 'SCHEDULED')            return '#e65100';
        return '#94a3b8';
    }

    statusLabel(s: string | undefined): string {
        const v = (s || '').toUpperCase();
        if (v === 'ENVOYE' || v === 'SENT')         return 'Envoyée';
        if (v === 'ECHEC'  || v === 'FAILED')       return 'Échec';
        if (v === 'PROGRAMME' || v === 'SCHEDULED') return 'Programmée';
        return v || 'Inconnue';
    }

    shortId(id: string | undefined | null): string {
        if (!id) return '—';
        if (id === 'ALL') return 'TOUS';
        return id.length > 10 ? id.substring(0, 10) + '…' : id;
    }

    fmtDate(iso: string | null | undefined): string {
        if (!iso) return '—';
        try {
            return new Date(String(iso).replace(' ', 'T')).toLocaleString('fr-FR', {
                day: '2-digit', month: '2-digit', year: 'numeric',
                hour: '2-digit', minute: '2-digit',
            });
        } catch { return String(iso); }
    }

    fmtRelative(iso: string | null | undefined): string {
        if (!iso) return '';
        try {
            const d = new Date(String(iso).replace(' ', 'T'));
            const diff = Date.now() - d.getTime();
            const min = Math.floor(diff / 60000);
            if (min < 1) return "à l'instant";
            if (min < 60) return `il y a ${min} min`;
            const h = Math.floor(min / 60);
            if (h < 24) return `il y a ${h}h`;
            const day = Math.floor(h / 24);
            if (day < 7) return `il y a ${day}j`;
            return '';
        } catch { return ''; }
    }

    private parseDate(s: string | null | undefined): Date | null {
        if (!s) return null;
        try { return new Date(String(s).replace(' ', 'T')); } catch { return null; }
    }

    /** Pour éviter de re-render toute la liste à chaque tick */
    trackByNotif(_: number, n: NotifRow): any { return n.id ?? (n.date_envoi || '') + (n.titre || ''); }
}