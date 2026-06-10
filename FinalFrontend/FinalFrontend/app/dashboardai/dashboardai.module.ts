import { NgModule } from "@angular/core";
import { CommonModule, DecimalPipe } from "@angular/common";
import { DashboardaiComponent } from "./dashboardai/dashboardai.component";
import { ProfilemodelComponent } from "./profilemodel/profilemodel.component";
import { RecommendationmodelComponent } from "./recommendationmodel/recommendationmodel.component";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { DashboardaiRoutingModule } from "./dashboardai.routing";
import { ModelheaderComponent } from "./modelheader/modelheader.component";
import { NotificationsComponent } from "./notifications/notifications.component";
import { AlertsComponent } from "./alerts/alerts.component";
import { RetrainMlComponent } from "./retrain-ml/retrain-ml.component";

@NgModule({
  declarations: [
    DashboardaiComponent,
    ProfilemodelComponent,
    RecommendationmodelComponent,
    ModelheaderComponent,
    NotificationsComponent,
    AlertsComponent,
    RetrainMlComponent,
  ],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    DashboardaiRoutingModule,
    DecimalPipe,
  ],
})
export class DashboardaiModule {}
