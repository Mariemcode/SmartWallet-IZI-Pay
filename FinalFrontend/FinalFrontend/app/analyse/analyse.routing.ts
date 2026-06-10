import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { GlobalsummaryComponent } from './globalsummary/globalsummary.component';
import { DepenseComponent } from './depense/depense.component';
import { RevenueComponent } from './revenue/revenue.component';
import { SouscategoryComponent } from './souscategory/souscategory.component';

const routes: Routes = [
  { path: '', redirectTo: 'global', pathMatch: 'full' },
  { path: 'global', component: GlobalsummaryComponent },
  { path: 'depense', component: DepenseComponent },
  { path: 'revenue', component: RevenueComponent},
  { path: 'souscat', component: SouscategoryComponent}, // sub category route 
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AnalyseRoutingModule {}
