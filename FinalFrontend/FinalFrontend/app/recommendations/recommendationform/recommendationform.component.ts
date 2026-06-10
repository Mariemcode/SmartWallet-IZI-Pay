import { Component, OnInit } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ProfileService } from '../../services/profile/profile.service';
import { NotificationService } from '../../services/recommendation/notif/notification.service';
import { OfferService } from '../../services/recommendation/offre/offer.service';
import { RecommendationService } from '../../services/recommendation/recommendation/recommendation.service';

@Component({
  selector: 'app-recommendationform',
  templateUrl: './recommendationform.component.html',
  styleUrls: ['./recommendationform.component.css']
})
export class RecommendationformComponent implements OnInit {
  form!: FormGroup;
  isEditMode = false;
  recommendationId: number | null = null;
  loading = false;
  profileOptions: string[] = [];
  offerOptions: { code: string; title: string; description?: string }[] = [];
  statusOptions = ['APPROVED', 'REJECTED'];
  preselectedOfferCode: string | null = null;
  selectedOfferDescription = '';
  recommendationDescription = '';

  constructor(
    private fb: FormBuilder,
    private recoService: RecommendationService,
    private offerService: OfferService,
    private profileService: ProfileService,
    private route: ActivatedRoute,
    private router: Router,
    private notification: NotificationService
  ) {
    this.initForm();
  }

  ngOnInit(): void {
    this.loadProfiles();
    this.loadActiveOffers();

    this.route.queryParams.subscribe(params => {
      this.preselectedOfferCode = params['offerCode'] || null;
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.isEditMode = true;
      this.recommendationId = +idParam;
      this.loadRecommendationForEdit();
    }
  }

  private initForm(): void {
    this.form = this.fb.group({
      profileName: ['', Validators.required],
      offerCode: ['', Validators.required],
      note: [''],
      description: [''],
      status: [{ value: 'APPROVED', disabled: true }]
    });
  }

  private loadProfiles(): void {
    this.profileService.getAllProfileNames().subscribe({
      next: (names) => (this.profileOptions = names),
      error: () => this.notification.showError('Erreur chargement profils')
    });
  }

  private loadActiveOffers(): void {
    this.offerService.listOffers({ status: 'ACTIVE', limit: 1000 }).subscribe({
      next: (page) => {
        this.offerOptions = page.offers.map(offer => ({
          code: offer.offerCode,
          title: offer.title,
          description: offer.description
        }));
        if (this.preselectedOfferCode && !this.isEditMode) {
          const exists = this.offerOptions.some(opt => opt.code === this.preselectedOfferCode);
          if (exists) {
            this.form.patchValue({ offerCode: this.preselectedOfferCode });
            this.updateOfferDescription(this.preselectedOfferCode);
            this.notification.showInfo(`Offre pré‑sélectionnée : ${this.preselectedOfferCode}`);
          } else {
            this.notification.showError(`Offre ${this.preselectedOfferCode} introuvable ou inactive`);
          }
          this.preselectedOfferCode = null;
        }
      },
      error: () => this.notification.showError('Erreur chargement offres')
    });
  }

  private loadRecommendationForEdit(): void {
    this.loading = true;
    this.recoService.getRecommendationById(this.recommendationId!).subscribe({
      next: (reco) => {
        this.form.patchValue({
          profileName: reco.profileName,
          offerCode: reco.offerCode,
          score: reco.score,
          note: reco.adminNote,
          description: reco.description || '',
          status: reco.status === 'PENDING' ? 'APPROVED' : reco.status
        });
        this.recommendationDescription = reco.description || '';
        this.updateOfferDescription(reco.offerCode);
        this.form.get('profileName')?.disable();
        this.form.get('offerCode')?.disable();
        this.form.get('score')?.disable();
        this.form.get('status')?.enable();
        this.loading = false;
      },
      error: () => {
        this.notification.showError('Recommandation introuvable');
        this.router.navigate(['/layout/recommendation/recommendations']);
      }
    });
  }

  onOfferChange(): void {
    const offerCode = this.form.get('offerCode')?.value;
    this.updateOfferDescription(offerCode);
  }

  private updateOfferDescription(offerCode: string): void {
    const offer = this.offerOptions.find(o => o.code === offerCode);
    this.selectedOfferDescription = offer?.description || '';
  }

  /** Génère une description via l’API Python (proxy Spring Boot) */
  generateAIDescription(): void {
    const offerCode = this.form.get('offerCode')?.value;
    if (!offerCode) {
      this.notification.showError('Veuillez d’abord sélectionner une offre');
      return;
    }
    this.loading = true;
    this.recoService.generateDescription(offerCode).subscribe({
      next: (desc) => {
        this.form.patchValue({ description: desc });
        this.loading = false;
        this.notification.showSuccess('Description générée par l’IA');
      },
      error: () => {
        this.notification.showError('Erreur lors de la génération de la description');
        this.loading = false;
      }
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.notification.showError('Formulaire invalide');
      return;
    }
    this.loading = true;

    if (this.isEditMode && this.recommendationId) {
      const payload = {
        status: this.form.get('status')?.value,
        note: this.form.get('note')?.value
      };
      this.recoService.updateRecommendationStatus(this.recommendationId, payload).subscribe({
        next: () => {
          this.notification.showSuccess('Recommandation mise à jour');
          this.router.navigate(['/layout/recommendation/recommendations']);
        },
        error: () => {
          this.notification.showError('Erreur mise à jour');
          this.loading = false;
        }
      });
    } else {
      const dto: any = {
        profileName: this.form.value.profileName,
        offerCode: this.form.value.offerCode,
        score: this.form.value.score,
        note: this.form.value.note,
        description: this.form.value.description
      };
      this.recoService.addManualRecommendation(dto).subscribe({
        next: () => {
          this.notification.showSuccess('Recommandation ajoutée (en attente d’approbation)');
          this.router.navigate(['/layout/recommendation/recommendations']);
        },
        error: () => {
          this.notification.showError('Erreur création');
          this.loading = false;
        }
      });
    }
  }

  cancel(): void {
    this.router.navigate(['/layout/recommendation/recommendations']);
  }
}