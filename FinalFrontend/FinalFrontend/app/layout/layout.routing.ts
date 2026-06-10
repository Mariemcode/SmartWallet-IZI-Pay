import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LayoutComponent } from './layout/layout.component';
import { DashboardComponent } from '../dashboard/dashboard.component';


const routes: Routes = [
  {
    path: '',
    component: LayoutComponent,
    children: [
      {
        path: 'client',  
        loadChildren: () =>
          import('../client/client.module').then((m) => m.ClientModule),
      },  
      {
        path: 'analyse',  
        loadChildren: () =>
          import('../analyse/analyse.module').then((m) => m.AnalyseModule),
      },    
      {
        path: 'provider',  
        loadChildren: () =>
          import('../provider/provider.module').then((m) => m.ProviderModule),
      },
      {path: 'dashboard', component: DashboardComponent}, 
       {
        path: 'profile',  
        loadChildren: () =>
          import('../profile/profile.module').then((m) => m.ProfileModule),
      },
       {
        path: 'recommendation',  
        loadChildren: () =>
          import('../recommendations/recommendations.module').then((m) => m.RecommendationsModule),
      },
      {
        path: 'models',  
        loadChildren: () =>
          import('../dashboardai/dashboardai.module').then((m) => m.DashboardaiModule),
      },
    ],
  },
];


@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class LayoutRoutingModule {}
