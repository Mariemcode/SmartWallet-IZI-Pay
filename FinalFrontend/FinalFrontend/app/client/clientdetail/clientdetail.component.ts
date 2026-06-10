import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Client } from '../../models/Client';
import { Transaction } from '../../models/Transaction';
import { ClientService } from '../../services/client/client.service';
import { TransactionService } from '../../services/transaction/transaction.service';
import { TransactionFilter } from '../../models/TransactionFilter';

@Component({
  selector: 'app-clientdetail',
  templateUrl: './clientdetail.component.html',
  styleUrls: ['./clientdetail.component.css']
})
export class ClientdetailComponent implements OnInit {

  client: Client | null = null;
  transactions: Transaction[] = [];
  categories: string[] = [];

  loadingClient       = true;
  loadingTransactions = true;
  error = '';

  // Filtres
  filter: TransactionFilter = {
    category: '',
    typeType: '',
    date: ''
  };

  // Pagination frontale
  currentPage = 0;
  pageSize = 10;
  totalPages = 0;

  private clientId!: string;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private clientService: ClientService,
    private transactionService: TransactionService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.clientId = id;

      // Client
      this.clientService.getClientById(id).subscribe({
        next: (data) => { this.client = data; this.loadingClient = false; },
        error: () => { this.error = 'Client introuvable.'; this.loadingClient = false; }
      });

      // Catégories pour le select
      this.transactionService.getCategories().subscribe({
        next: (cats) => this.categories = cats
      });

      // Transactions initiales (sans filtre)
      this.loadTransactions();
    }
  }

  // Getter pour les transactions de la page courante (découpage du tableau)
  get paginatedTransactions(): Transaction[] {
    const start = this.currentPage * this.pageSize;
    return this.transactions.slice(start, start + this.pageSize);
  }

  // Chargement des transactions (une seule requête HTTP)
  loadTransactions(): void {
    this.loadingTransactions = true;
    const hasFilter = this.filter.category || this.filter.typeType || this.filter.date;

    const obs = hasFilter
      ? this.transactionService.getTransactionsFiltered(this.clientId, this.filter)
      : this.transactionService.getTransactionsByClientId(this.clientId);

    obs.subscribe({
      next: (data) => {
        this.transactions = data;
        this.totalPages = Math.ceil(this.transactions.length / this.pageSize);
        this.currentPage = 0;   // retour à la première page après filtrage
        this.loadingTransactions = false;
      },
      error: () => {
        this.loadingTransactions = false;
      }
    });
  }

  // Appliquer les filtres
  applyFilter(): void {
    this.currentPage = 0;
    this.loadTransactions();
  }

  // Réinitialiser les filtres
  resetFilter(): void {
    this.filter = { category: '', typeType: '', date: '' };
    this.currentPage = 0;
    this.loadTransactions();
  }

  // Changer de page
  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.currentPage = page;
  }

  // Changer la taille de page
  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.totalPages = Math.ceil(this.transactions.length / this.pageSize);
    this.currentPage = 0;
  }

  // Indicateur de chargement global
  get loading(): boolean {
    return this.loadingClient || this.loadingTransactions;
  }

  // Vérifier si la transaction est un reversal
  isReversal(tx: Transaction): boolean {
    return tx.reversalFlag === 'Y';
  }

  goBack(): void {
    this.router.navigate(['/layout/client/clients']);
  }
}