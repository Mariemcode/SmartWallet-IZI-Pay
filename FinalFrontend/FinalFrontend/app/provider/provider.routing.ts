import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ProviderlistComponent } from './providerlist/providerlist.component';
import { ProviderdetailsComponent } from './providerdetails/providerdetails.component';


const routes: Routes = [
    { path: '', redirectTo: 'providers', pathMatch: 'full' },
  { path: 'providers',          component: ProviderlistComponent },
  { path: 'providers/:id/stats', component: ProviderdetailsComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ProviderRoutingModule {}
