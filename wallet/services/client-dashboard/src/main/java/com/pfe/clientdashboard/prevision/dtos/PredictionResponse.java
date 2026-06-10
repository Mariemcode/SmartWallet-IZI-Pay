package com.pfe.clientdashboard.prevision.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PredictionResponse {

    @JsonProperty("client_id") private String clientId;
    @JsonProperty("client_nom") private String clientNom;
    @JsonProperty("solde_actuel_TND") private Double soldeActuelTnd;
    @JsonProperty("generated_at") private String generatedAt;
    @JsonProperty("segment") private String segment;
    @JsonProperty("mois_prevu") private String moisPrevu;
    @JsonProperty("version") private String version;

    @JsonProperty("module1_factures") private Module1Factures module1Factures;
    @JsonProperty("module2_recharge") private Module2Recharge module2Recharge;
    @JsonProperty("module3_budget") private Module3Budget module3Budget;
    @JsonProperty("module4_prochaine_tx") private Module4ProchaineTx module4ProchaineTx;
    @JsonProperty("module5_alerte") private Module5Alerte module5Alerte;

    // --- Sous-classes nettoyées ---
    @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Module1Factures {
        @JsonProperty("nb_factures") private Integer nbFactures;
        @JsonProperty("factures") private List<Facture> factures;
        @JsonProperty("total_a_venir_TND") private Double totalAVenirTnd;

        // SUPPRIMÉ : nbPayees, nbEnRetard, verificationLive, totalActif, etc.
    }

    @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Facture {
        @JsonProperty("fournisseur") private String fournisseur;
        @JsonProperty("label") private String label;
        @JsonProperty("montant_prevu") private Double montantPrevu;
        @JsonProperty("ic_bas") private Double icBas;
        @JsonProperty("ic_haut") private Double icHaut;
        @JsonProperty("date_prevue") private String datePrevue;
        @JsonProperty("jours_restants") private Integer joursRestants;
        @JsonProperty("statut") private String statut; // "urgente", "prochaine", "planifiée"
        @JsonProperty("confiance") private String confiance;
        @JsonProperty("r2_modele") private Double r2Modele;
        @JsonProperty("intervalle_median") private Integer intervalleMedian;

        // SUPPRIMÉ : sousStatut, datePaiement, montantPaye, joursRetard, avertissement, prochaineDansJours, etc.
    }

    @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Module2Recharge {
        @JsonProperty("client_id") private String clientId;
        @JsonProperty("erreur") private String erreur;
        @JsonProperty("operateur") private String operateur;
        @JsonProperty("montant_habituel") private Double montantHabituel;
        @JsonProperty("intervalle_median") private Integer intervalleMedian;
        @JsonProperty("date_prevue") private String datePrevue;
        @JsonProperty("jours_restants") private Integer joursRestants;
        @JsonProperty("statut") private String statut;
        @JsonProperty("fiabilite") private String fiabilite;
        @JsonProperty("n_historique") private Integer nHistorique;
    }

    @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Module3Budget {
        @JsonProperty("client_id") private String clientId;
        @JsonProperty("segment") private String segment;
        @JsonProperty("mois_prevu") private String moisPrevu;
        @JsonProperty("budget_total_TND") private Double budgetTotalTnd;
        @JsonProperty("par_categorie") private Map<String, DetailBudget> parCategorie;
    }

    @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DetailBudget {
        @JsonProperty("predit") private Double predit;
        @JsonProperty("ic_bas") private Double icBas;
        @JsonProperty("ic_haut") private Double icHaut;
        @JsonProperty("confiance") private String confiance;
    }

    @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Module4ProchaineTx {
        @JsonProperty("client_id") private String clientId;
        @JsonProperty("erreur") private String erreur;
        @JsonProperty("cat_dominante") private String catDominante;
        @JsonProperty("delai_estime_jours") private Integer delaiEstimeJours;
        @JsonProperty("jours_depuis_tx") private Integer joursDepuisTx;
        @JsonProperty("confiance") private String confiance;
        @JsonProperty("top3") private List<ProbaTx> top3;
    }

    @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProbaTx {
        @JsonProperty("categorie") private String categorie;
        @JsonProperty("probabilite") private Double probabilite;
        @JsonProperty("dans_jours") private Integer dansJours;
    }

    @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Module5Alerte {
        @JsonProperty("client_id") private String clientId;
        @JsonProperty("solde_actuel_TND") private Double soldeActuelTnd;
        @JsonProperty("total_urgent_TND") private Double totalUrgentTnd;
        @JsonProperty("budget_variable_TND") private Double budgetVariableTnd;
        @JsonProperty("total_prevu_TND") private Double totalPrevuTnd;
        @JsonProperty("solde_apres_TND") private Double soldeApresTnd;
        @JsonProperty("niveau_alerte") private String niveauAlerte;
        @JsonProperty("nb_urgents") private Integer nbUrgents;
        @JsonProperty("factures_urgentes") private List<Object> facturesUrgentes;
        @JsonProperty("message") private String message;
        @JsonProperty("recommandation") private String recommandation;
    }
}