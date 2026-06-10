import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { OfferResponse, RecommendationResponse, ClientProfileDTO } from '../../models/recommendation.models';
import { NotificationService } from '../../services/recommendation/notif/notification.service';
import { RecommendationService } from '../../services/recommendation/recommendation/recommendation.service';
import { OfferService } from '../../services/recommendation/offre/offer.service';
import { RecommendationmetricsService } from '../../services/recommendation/RecommendationMetrics/recommendationmetrics.service';

@Component({
  selector: 'app-recommendationdetail',
  templateUrl: './recommendationdetail.component.html',
  styleUrls: ['./recommendationdetail.component.css']
})
export class RecommendationdetailComponent implements OnInit {
  recommendation: RecommendationResponse | null = null;
  offerDetails: OfferResponse | null = null;
  loading = false;
  notifying = false;

  // Clients du profil
  clients: ClientProfileDTO[] = [];
  clientsTotalElements = 0;
  clientsCurrentPage = 0;
  clientsPageSize = 10;
  clientsLoading = false;
  pageSizes = [5, 10, 20, 50];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private recoService: RecommendationService,
    private offerService: OfferService,
    private metricsService: RecommendationmetricsService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) this.loadRecommendation(id);
    else this.router.navigate(['/layout/recommendation/recommendations']);
  }

  loadRecommendation(id: number): void {
    this.loading = true;
    this.recoService.getRecommendationById(id).subscribe({
      next: (recommendation) => {
        this.recommendation = recommendation;
        this.loadOfferDetails(recommendation.offerCode);
        this.loadClients();
        this.loading = false;
      },
      error: (err) => {
        console.warn('Fallback : chargement de toutes les recommandations', err);
        this.recoService.getRecommendations({ limit: 1000, offset: 0 }).subscribe({
          next: (page) => {
            const found = page.recommendations.find(r => r.id === id);
            if (found) {
              this.recommendation = found;
              this.loadOfferDetails(found.offerCode);
              this.loadClients();
            } else {
              this.notificationService.showError('Recommandation non trouvée');
              this.router.navigate(['/layout/recommendation/recommendations']);
            }
            this.loading = false;
          },
          error: () => {
            this.notificationService.showError('Erreur chargement');
            this.loading = false;
            this.router.navigate(['/layout/recommendation/recommendations']);
          }
        });
      }
    });
  }

  loadOfferDetails(offerCode: string): void {
    this.offerService.getOffer(offerCode).subscribe({
      next: (offer) => { this.offerDetails = offer; },
      error: (err) => { console.error('Erreur chargement offre', err); }
    });
  }

  loadClients(): void {
    if (!this.recommendation) return;
    this.clientsLoading = true;
    this.recoService.getClientsByProfile(
      this.recommendation.profileName,
      this.clientsCurrentPage,
      this.clientsPageSize
    ).subscribe({
      next: (data) => {
        this.clients = data.clients;
        this.clientsTotalElements = data.totalElements;
        this.clientsCurrentPage = data.currentPage;
        this.clientsLoading = false;
      },
      error: (err) => {
        console.error(err);
        this.clientsLoading = false;
      }
    });
  }

  onClientsPageChange(page: number): void {
    this.clientsCurrentPage = page;
    this.loadClients();
  }

  onClientsPageSizeChange(size: number): void {
    this.clientsPageSize = +size;
    this.clientsCurrentPage = 0;
    this.loadClients();
  }

  get clientsTotalPages(): number {
    return Math.ceil(this.clientsTotalElements / this.clientsPageSize);
  }

  notifyClients(): void {
    if (!this.recommendation) return;
    if (this.recommendation.status !== 'APPROVED') {
      this.notificationService.showError('Seules les recommandations approuvées peuvent être notifiées.');
      return;
    }
    const profileName = this.recommendation.profileName;
    if (!profileName) {
      this.notificationService.showError('Aucun profil associé à cette recommandation');
      return;
    }
    if (confirm(`Envoyer une notification à tous les clients du profil "${profileName}" ?`)) {
      this.notifying = true;
      this.metricsService.sendNotifications(profileName).subscribe({
        next: (result) => {
          this.notificationService.showSuccess(
            `Notification envoyée à ${result.sent} client(s) du profil ${profileName}`
          );
          this.notifying = false;
        },
        error: (err) => {
          console.error(err);
          this.notificationService.showError('Erreur lors de l’envoi des notifications');
          this.notifying = false;
        }
      });
    }
  }

  approve(): void {
    if (!this.recommendation) return;
    const note = prompt('Note d’approbation (optionnelle) :');
    this.recoService.approveRecommendation(this.recommendation.id, note || undefined).subscribe({
      next: (updated) => {
        this.recommendation = updated;
        this.notificationService.showSuccess('Recommandation approuvée');
      },
      error: () => this.notificationService.showError('Erreur approbation')
    });
  }

  reject(): void {
    if (!this.recommendation) return;
    const note = prompt('Motif du rejet :');
    if (note === null) return;
    this.recoService.rejectRecommendation(this.recommendation.id, note).subscribe({
      next: (updated) => {
        this.recommendation = updated;
        this.notificationService.showSuccess('Recommandation rejetée');
      },
      error: () => this.notificationService.showError('Erreur rejet')
    });
  }

  editRecommendation(): void {
    if (this.recommendation) {
      this.router.navigate(['/layout/recommendation/recommendations/edit', this.recommendation.id]);
    }
  }

  goBack(): void {
    this.router.navigate(['/layout/recommendation/recommendations']);
  }

  viewOfferDetails(): void {
    if (this.recommendation) {
      this.router.navigate(['/layout/recommendation/offer', this.recommendation.offerCode]);
    }
  }

  viewProfileDetails(): void {
    if (this.recommendation && this.recommendation.clusterId !== undefined && this.recommendation.clusterId !== -1) {
      this.router.navigate(['/layout/profile/profiles/', this.recommendation.clusterId]);
    } else {
      this.notificationService.showInfo('Profil non disponible (mixte ou inconnu)');
    }
  }
}