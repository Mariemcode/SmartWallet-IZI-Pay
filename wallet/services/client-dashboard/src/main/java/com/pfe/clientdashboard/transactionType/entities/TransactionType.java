package com.pfe.clientdashboard.transactionType.entities;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité mappée sur la table "type_transaction"
 *
 * Colonnes dataset :
 *   id, code, title, original_title, type,
 *   category, sub_category
 *   (+ created_by, create_date_time, modified_by, modified_date_time ignorés)
 *
 * type :
 *   "C" = Crédit  → argent qui ENTRE dans le wallet
 *   "D" = Débit   → argent qui SORT  du wallet
 *
 * 12 catégories dashboard présentes dans le dataset :
 *   Argent Recu
 *   Transferts Recus
 *   Depot & Retrait
 *   Factures & Services
 *   Recharge Telephonique
 *   Restaurants & Livraison
 *   Shopping & Paiements
 *   Voyages & Reservations
 *   Education & Institutions
 *   Transferts Envoyes
 *   Frais & Commissions
 *   Annulation & Correction
 */
@Entity
@Table(name = "type_transaction")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TransactionType {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "code")
    private String code;

    @Column(name = "title")
    private String title;

    @Column(name = "original_title")
    private String originalTitle;

    /**
     * "C" = Crédit (argent entrant)
     * "D" = Débit  (argent sortant)
     */
    @Column(name = "type", nullable = false, length = 1)
    private String type;

    /** Catégorie dashboard (ex: "Factures & Services") */
    @Column(name = "category")
    private String category;

    /** Sous-catégorie (ex: "Paiement de facture") */
    @Column(name = "sub_category")
    private String subCategory;
}
