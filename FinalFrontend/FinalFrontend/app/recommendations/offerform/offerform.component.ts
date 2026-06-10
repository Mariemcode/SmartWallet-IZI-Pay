import { Component, OnInit } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';

import { OfferResponse } from '../../models/recommendation.models';
import { ProfileService } from '../../services/profile/profile.service';
import { NotificationService } from '../../services/recommendation/notif/notification.service';
import { OfferService } from '../../services/recommendation/offre/offer.service';
import { ProviderService } from '../../services/provider/provider.service';
import { TransactionService } from '../../services/transaction/transaction.service';

@Component({
  selector: 'app-offerform',
  templateUrl: './offerform.component.html',
  styleUrl: './offerform.component.css'
})
export class OfferformComponent implements OnInit {
  offerForm!: FormGroup;
  isEditMode = false;
  offerCode: string | null = null;
  loading = false;

  profileOptions: string[] = [];
  typeOptions: string[] = [];
  providerOptions: string[] = [];
  categoryOptions: string[] = [];

  constructor(
    private fb: FormBuilder,
    private offerService: OfferService,
    private profileService: ProfileService,
    private providerService: ProviderService,
    private transactionService: TransactionService,
    private route: ActivatedRoute,
    private router: Router,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.initForm();

    this.offerCode = this.route.snapshot.paramMap.get('offerCode');
    if (this.offerCode) {
      this.isEditMode = true;
    }

    this.loading = true;
    forkJoin({
      profiles:   this.profileService.getAllProfileNames(),
      types:      this.offerService.listOffers({ limit: 1000, offset: 0 }),
      providers:  this.providerService.getAllProviders(),
      categories: this.transactionService.getCategories()
    }).subscribe({
      next: ({ profiles, types, providers, categories }) => {
        this.profileOptions = profiles;

        const uniqueTypes = new Set(types.offers.map(o => o.type).filter(t => t));
        this.typeOptions = Array.from(uniqueTypes);

        const uniqueNames = new Set(providers.map(p => p.providerName).filter(n => n));
        this.providerOptions = Array.from(uniqueNames);

        this.categoryOptions = categories;

        this.loading = false;

        if (this.isEditMode && this.offerCode) {
          this.loadOfferForEdit();
        }
      },
      error: (err) => {
        console.error('Erreur chargement des listes', err);
        this.notificationService.showError('Erreur lors du chargement du formulaire');
        this.loading = false;
      }
    });
  }

  private initForm(): void {
    this.offerForm = this.fb.group({
      title:          ['', [Validators.required, Validators.maxLength(300)]],
      type:           ['', Validators.required],
      providerName:   [''],
      category:       [''],
      discountPct:    [0, [Validators.required, Validators.min(0), Validators.max(100)]],
      minAmount:      [0, [Validators.required, Validators.min(0)]],
      targetProfiles: [[], [Validators.required, Validators.minLength(1)]],
      boost:          [1, [Validators.required, Validators.min(0.1)]],
      description:    ['']
    });
  }

  private loadOfferForEdit(): void {
    this.loading = true;
    this.offerService.getOffer(this.offerCode!).subscribe({
      next: (offer: OfferResponse) => {
        this.ensureOptionExists(this.typeOptions,     offer.type);
        this.ensureOptionExists(this.providerOptions, offer.providerName);
        this.ensureOptionExists(this.categoryOptions, offer.category);

        this.offerForm.patchValue({
          title:          offer.title,
          type:           offer.type,
          providerName:   offer.providerName,
          category:       offer.category,
          cashbackPct:    offer.cashbackPct,
          discountPct:    offer.discountPct,
          minAmount:      offer.minAmount,
          targetProfiles: offer.targetProfiles,
          boost:          offer.boost,
          description:    offer.description
        });

        this.loading = false;
      },
      error: (err) => {
        console.error(err);
        this.notificationService.showError('Offre non trouvée');
        this.router.navigate(['/layout/recommendation/offers']);
      }
    });
  }

  private ensureOptionExists(options: string[], value: string): void {
    if (value && !options.includes(value)) {
      options.unshift(value);
    }
  }

  onSubmit(): void {
    if (this.offerForm.invalid) {
      this.markAllAsTouched();
      this.notificationService.showError('Formulaire invalide, vérifiez les champs');
      return;
    }

    const formValue = this.offerForm.value;
    this.loading = true;

    if (this.isEditMode && this.offerCode) {
      this.offerService.updateOffer(this.offerCode, formValue).subscribe({
        next: () => {
          this.notificationService.showSuccess('Offre modifiée avec succès');
          this.router.navigate(['/layout/recommendation/offer', this.offerCode]);
        },
        error: (err) => {
          console.error(err);
          this.notificationService.showError('Erreur lors de la modification');
          this.loading = false;
        }
      });
    } else {
      this.offerService.createOffer(formValue).subscribe({
        next: (created) => {
          this.notificationService.showSuccess('Offre créée avec succès');
          this.router.navigate(['/layout/recommendation/offer', created.offerCode]);
        },
        error: (err) => {
          console.error(err);
          this.notificationService.showError('Erreur lors de la création');
          this.loading = false;
        }
      });
    }
  }

  private markAllAsTouched(): void {
    Object.keys(this.offerForm.controls).forEach(key => {
      this.offerForm.get(key)?.markAsTouched();
    });
  }

  cancel(): void {
    if (this.isEditMode && this.offerCode) {
      this.router.navigate(['/layout/recommendation/offer', this.offerCode]);
    } else {
      this.router.navigate(['/layout/recommendation/offers']);
    }
  }
}