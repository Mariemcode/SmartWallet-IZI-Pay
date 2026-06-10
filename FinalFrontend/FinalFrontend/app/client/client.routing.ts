import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ClientlistComponent } from './clientlist/clientlist.component';
import { ClientdetailComponent } from './clientdetail/clientdetail.component';

const routes: Routes = [
    { path: '', redirectTo: 'clients', pathMatch: 'full' },
  { path: 'clients', component: ClientlistComponent },
  { path: 'detail/:id', component: ClientdetailComponent }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ClientRoutingModule {}
