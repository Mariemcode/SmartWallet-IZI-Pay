package com.pfe.clientdashboard.client.entities;

import com.pfe.clientdashboard.transaction.entities.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entité mappée sur la table "client"
 *
 * Colonnes dataset :
 *   id, first_name, last_name, phone_number, create_date_time
 *
 * Note: phone_number est l'identifiant métier (numéro IZI Pay)
 * Note: first_name/last_name sont NULL dans le dataset (données anonymisées)
 */
@Entity
@Table(name = "client")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Client {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "create_date_time")
    private LocalDateTime createDateTime;

//    // ── Colonnes ML (issues de client_enriched_corrected.csv) ────────
//
//    /** Profil de dépenses ML : "actif" / "moyen" / "petit" / "tres_actif" */
//    @Column(name = "profil_ml")
//    private String profilMl;
//
//    /** Style de vie : "etudiant" / "jeune_actif" / "salarie" / "famille" / "professionnel" */
//    @Column(name = "style_vie")
//    private String styleVie;
//
//    /** Sexe : "M" ou "F" */
//    @Column(name = "sexe", length = 1)
//    private String sexe;
//
//    /** Tranche d'âge : "18-25" / "26-35" / "36-45" / "46+" */
//    @Column(name = "tranche_age")
//    private String trancheAge;
//
//    /** Gouvernorat de résidence */
//    @Column(name = "gouvernorat")
//    private String gouvernorat;

    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY)
    private List<Transaction> transactions;
}