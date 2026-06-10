package com.pfe.clientdashboard.transactionType.repository;

import com.pfe.clientdashboard.transactionType.entities.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionTypeRepository extends JpaRepository<TransactionType, String> {

    /**
     * Toutes les catégories distinctes triées alphabétiquement.
     * Utilisé pour alimenter les filtres de l'interface.
     *
     * Les 12 catégories du dataset :
     *   Annulation & Correction, Argent Recu, Depot & Retrait,
     *   Education & Institutions, Factures & Services, Frais & Commissions,
     *   Recharge Telephonique, Restaurants & Livraison, Shopping & Paiements,
     *   Transferts Envoyes, Transferts Recus, Voyages & Reservations
     */
    @Query("""
           SELECT DISTINCT tt.category
           FROM TransactionType tt
           WHERE tt.category IS NOT NULL
           ORDER BY tt.category
           """)
    List<String> findAllDistinctCategories();

    /** Sous-catégories pour une catégorie donnée */
    @Query("""
           SELECT DISTINCT tt.subCategory
           FROM TransactionType tt
           WHERE tt.category = :category
             AND tt.subCategory IS NOT NULL
           """)
    List<String> findSubCategoriesByCategory(@Param("category") String category);



        @Query("SELECT DISTINCT tt.category FROM TransactionType tt ORDER BY tt.category")
        List<String> findDistinctCategories();

}
