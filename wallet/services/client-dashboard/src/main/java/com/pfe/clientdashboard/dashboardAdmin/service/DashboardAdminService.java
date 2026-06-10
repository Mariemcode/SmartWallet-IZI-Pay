package com.pfe.clientdashboard.dashboardAdmin.service;

import com.pfe.clientdashboard.dashboardAdmin.repository.DashboardRepository;
import com.pfe.clientdashboard.dashboardAdmin.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardAdminService {

    private final DashboardRepository repo;

    /* ════════════════════════════════════════════════════════════════ */
    /* DASHBOARD — toutes les données jusqu'à maintenant              */
    /* ════════════════════════════════════════════════════════════════ */

    //Retourne en une seule fois toutes les données nécessaires à l'affichage du dashboard :
    // KPIs, activité journalière, répartition débit/crédit, top providers et top catégories.
    public DashboardResponseDTO getDashboard() {
        return new DashboardResponseDTO(
                buildKpi(),
                buildDailyActivity(),
                buildDebitCredit(),
                buildTopProviders(),
                buildTopCategories()
        );
    }

    /* ── helpers ────────────────────────────────────────────────────── */

    //Convertit un Object numérique en BigDecimal arrondi à 3 décimales
    private BigDecimal toBD(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return new BigDecimal(((Number) o).doubleValue())
                .setScale(3, RoundingMode.HALF_UP);
    }
    //Convertit un Object en long via l'interface Number
    private long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
    //Convertit un Object en double primitif. Retourne 0.0 si null.
    private double toDouble(Object o) {
        return o == null ? 0.0 : ((Number) o).doubleValue();
    }
    // Calcule un pourcentage entier sur des longs. Utilise une précision intermédiaire à 1 décimale (* 1000.0 / ... / 10.0
    //Retourne 0 si total vaut 0
    private double pct(long part, long total) {
        if (total == 0) return 0.0;
        return Math.round(part * 1000.0 / total) / 10.0;
    }
    // Même calcul de pourcentage mais pour des BigDecimal
    private double pctBD(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return Math.round(part.doubleValue() * 1000.0 / total.doubleValue()) / 10.0;
    }

    /* ── KPIs ───────────────────────────────────────────────────────── */

    //Construit les 5 indicateurs clés du dashboard.
    // Appelle getKpiRaw() qui retourne une seule ligne avec 5 colonnes : count total, montant total, montant moyen, nombre de clients distincts, nombre de providers distincts
    private DashboardKpiDTO buildKpi() {
        List<Object[]> rows = repo.getKpiRaw();
        if (rows.isEmpty()) return new DashboardKpiDTO();
        Object[] r = rows.get(0);
        return new DashboardKpiDTO(
                toLong(r[0]),
                toBD(r[1]),
                toBD(r[2]),
                toLong(r[3]),
                toLong(r[4])
        );
    }

    /* ── Activité journalière ───────────────────────────────────────── */

    //Construit la liste des points de la courbe d'activité sur 30 jours.
    // Chaque ligne retournée par getDailyActivity() contient une date (String), un count de transactions, et un montant total.
    // Convertit chaque ligne en DailyActivityDTO
    private List<DailyActivityDTO> buildDailyActivity() {
        List<Object[]> rows = repo.getDailyActivity();
        List<DailyActivityDTO> list = new ArrayList<>();
        for (Object[] r : rows) {
            list.add(new DailyActivityDTO(
                    (String) r[0],
                    toLong(r[1]),
                    toBD(r[2])
            ));
        }
        return list;
    }

    /* ── Débit / Crédit ─────────────────────────────────────────────── */

    //Construit la répartition débit/crédit.
    // Appelle getDebitCreditRaw() qui retourne au maximum 2 lignes (une pour D, une pour C)
    private DebitCreditDTO buildDebitCredit() {
        List<Object[]> rows = repo.getDebitCreditRaw();

        long       dCount = 0, cCount = 0;
        BigDecimal dAmt   = BigDecimal.ZERO, cAmt = BigDecimal.ZERO;
        BigDecimal dAvg   = BigDecimal.ZERO, cAvg = BigDecimal.ZERO;

        for (Object[] r : rows) {
            String type = String.valueOf(r[0]);
            if ("D".equals(type)) {
                dCount = toLong(r[1]);  dAmt = toBD(r[2]);  dAvg = toBD(r[3]);
            } else if ("C".equals(type)) {
                cCount = toLong(r[1]);  cAmt = toBD(r[2]);  cAvg = toBD(r[3]);
            }
        }
        long       totalCount = dCount + cCount;
        BigDecimal totalAmt   = dAmt.add(cAmt);

        return new DebitCreditDTO(
                dCount, dAmt, pct(dCount, totalCount), pctBD(dAmt, totalAmt),
                cCount, cAmt, pct(cCount, totalCount), pctBD(cAmt, totalAmt),
                dAvg,   cAvg
        );
    }

    /* ── Top Providers ──────────────────────────────────────────────── */

    //Construit le classement des 3 meilleurs providers.
    // Appelle getTopProviders() pour les données des providers et getGrandTotals() pour avoir le dénominateur global (count et montant total toutes transactions)
    //Si getGrandTotals() est vide utilise 1 comme valeur par défaut pour éviter une division par zéro.
    private List<TopProviderDTO> buildTopProviders() {
        List<Object[]> rows   = repo.getTopProviders();
        List<Object[]> totals = repo.getGrandTotals();

        long       grandCount = totals.isEmpty() ? 1L  : toLong(totals.get(0)[0]);
        BigDecimal grandAmt   = totals.isEmpty() ? BigDecimal.ONE : toBD(totals.get(0)[1]);

        List<TopProviderDTO> list = new ArrayList<>();
        for (Object[] r : rows) {
            long       cnt = toLong(r[3]);
            BigDecimal amt = toBD(r[4]);
            list.add(new TopProviderDTO(
                    (String) r[0], (String) r[1], (String) r[2],
                    cnt, amt,
                    pct(cnt, grandCount),
                    pctBD(amt, grandAmt)
            ));
        }
        return list;
    }

    /* ── Top Catégories ─────────────────────────────────────────────── */

    //Même logique que buildTopProviders() mais pour les 3 catégories les plus actives.
    // Appelle getTopCategories()
    private List<TopCategoryDTO> buildTopCategories() {
        List<Object[]> rows   = repo.getTopCategories();
        List<Object[]> totals = repo.getGrandTotals();

        long       grandCount = totals.isEmpty() ? 1L  : toLong(totals.get(0)[0]);
        BigDecimal grandAmt   = totals.isEmpty() ? BigDecimal.ONE : toBD(totals.get(0)[1]);

        List<TopCategoryDTO> list = new ArrayList<>();
        for (Object[] r : rows) {
            long       cnt = toLong(r[1]);
            BigDecimal amt = toBD(r[2]);
            list.add(new TopCategoryDTO(
                    (String) r[0], cnt, amt,
                    pct(cnt, grandCount),
                    pctBD(amt, grandAmt)
            ));
        }
        return list;
    }
}