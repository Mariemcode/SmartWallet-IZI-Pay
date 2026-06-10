import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
  OfferResponse,
  OfferStatusUpdate,
  RecommendationResponse,
} from '../../models/recommendation.models';
import { NotificationService } from '../../services/recommendation/notif/notification.service';
import { OfferService } from '../../services/recommendation/offre/offer.service';
import { RecommendationService } from '../../services/recommendation/recommendation/recommendation.service';

@Component({
  selector: 'app-offerdetail',
  templateUrl: './offerdetail.component.html',
  styleUrls: ['./offerdetail.component.css'],
})
export class OfferdetailComponent implements OnInit {
  offerCode: string = '';
  offer: OfferResponse | null = null;
  loading = false;
  recommendations: RecommendationResponse[] = [];
  loadingRecos = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private offerService: OfferService,
    private recommendationService: RecommendationService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.offerCode = this.route.snapshot.paramMap.get('offerCode') || '';
    if (!this.offerCode) {
      this.router.navigate(['/layout/recommendation/offers']);
      return;
    }
    this.loadOfferDetails();
  }

  loadOfferDetails(): void {
    this.loading = true;
    this.offerService.getOffer(this.offerCode).subscribe({
      next: (data) => {
        this.offer = data;
        this.loading = false;
        this.loadRecommendations();
      },
      error: () => {
        this.notificationService.showError('Offre non trouvée');
        this.router.navigate(['/layout/recommendation/offers']);
      }
    });
  }

  // ✅ Navigue vers OfferformComponent en mode édition (même formulaire que création)
  goToEdit(): void {
    this.router.navigate(['/layout/recommendation/offers/edit', this.offerCode]);
  }

  toggleStatus(): void {
    if (!this.offer) return;
    const newStatus: 'ACTIVE' | 'INACTIVE' =
      this.offer.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    const label = newStatus === 'ACTIVE' ? 'activer' : 'désactiver';
    if (!confirm(`Voulez-vous vraiment ${label} cette offre ?`)) return;

    const statusUpdate: OfferStatusUpdate = { status: newStatus };
    this.offerService.updateOfferStatus(this.offerCode, statusUpdate).subscribe({
      next: (updated) => {
        this.offer = updated;
        this.notificationService.showSuccess(`Offre ${label}e avec succès`);
      },
      error: () => this.notificationService.showError(`Erreur lors de ${label} l'offre`)
    });
  }

  deleteOffer(): void {
    if (!confirm('⚠️ Supprimer définitivement cette offre ?')) return;
    this.offerService.deleteOffer(this.offerCode).subscribe({
      next: () => {
        this.notificationService.showSuccess('Offre supprimée');
        this.router.navigate(['/layout/recommendation/offers']);
      },
      error: (err) => {
        if (err.status === 500 && err.error?.message?.includes('foreign key constraint')) {
          this.notificationService.showError(
            '❌ Impossible de supprimer : des recommandations sont associées à cette offre.'
          );
        } else {
          this.notificationService.showError('Erreur lors de la suppression');
        }
      }
    });
  }

  loadRecommendations(): void {
    if (!this.offer) return;
    this.loadingRecos = true;
    this.recommendationService.getRecommendations({ limit: 500 }).subscribe({
      next: (page) => {
        this.recommendations = page.recommendations.filter(
          r => r.offerCode === this.offer!.offerCode
        );
        this.loadingRecos = false;
      },
      error: () => { this.loadingRecos = false; }
    });
  }

  createRecommendation(): void {
    this.router.navigate(['/layout/recommendation/recommendationsForm'], {
      queryParams: { offerCode: this.offer?.offerCode }
    });
  }

  viewRecommendationDetails(id: number): void {
    this.router.navigate(['/layout/recommendation/recommendations', id]);
  }

  updateRecommendationStatus(
    rec: RecommendationResponse,
    newStatus: 'APPROVED' | 'REJECTED'
  ): void {
    const action = newStatus === 'APPROVED' ? 'approuver' : 'rejeter';
    if (!confirm(`Voulez-vous vraiment ${action} la recommandation pour "${rec.profileName}" ?`)) return;

    this.recommendationService
      .updateRecommendationStatus(rec.id, { status: newStatus })
      .subscribe({
        next: (updated) => {
          const i = this.recommendations.findIndex(r => r.id === updated.id);
          if (i !== -1) this.recommendations[i] = updated;
          this.notificationService.showSuccess(`Recommandation ${action}e avec succès`);
        },
        error: () =>
          this.notificationService.showError(`Erreur lors de ${action} la recommandation`)
      });
  }

  goBack(): void {
    this.router.navigate(['/layout/recommendation/offers']);
  }
}