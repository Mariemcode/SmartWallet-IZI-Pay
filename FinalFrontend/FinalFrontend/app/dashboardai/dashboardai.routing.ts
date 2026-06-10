import { NgModule } from "@angular/core";
import { RouterModule, Routes } from "@angular/router";
import { DashboardaiComponent } from "./dashboardai/dashboardai.component";
import { ProfilemodelComponent } from "./profilemodel/profilemodel.component";
import { RecommendationmodelComponent } from "./recommendationmodel/recommendationmodel.component";
import { NotificationsComponent } from "./notifications/notifications.component";
import { AlertsComponent } from "./alerts/alerts.component";
import { RetrainMlComponent } from "./retrain-ml/retrain-ml.component";

const routes: Routes = [
  {
    path: "",
    component: DashboardaiComponent,
    children: [
      { path: "", redirectTo: "profilemodel", pathMatch: "full" },
      { path: "profilemodel",component: ProfilemodelComponent},
      { path: "recommendationmodel", component: RecommendationmodelComponent },
      { path: "notification", component: NotificationsComponent },
      { path: "alerts", component: AlertsComponent },
      { path: "retrain", component: RetrainMlComponent },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class DashboardaiRoutingModule {}
