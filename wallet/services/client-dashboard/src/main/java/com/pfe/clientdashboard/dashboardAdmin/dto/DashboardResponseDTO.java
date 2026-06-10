package com.pfe.clientdashboard.dashboardAdmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class DashboardResponseDTO {
    private DashboardKpiDTO        kpi;
    private List<DailyActivityDTO> dailyActivity;
    private DebitCreditDTO         debitCredit;
    private List<TopProviderDTO>   topProviders;
    private List<TopCategoryDTO>   topCategories;
    // topClients supprimé
}