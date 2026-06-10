import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ProfilelisteComponent } from './profileliste/profileliste.component';
import { ProfiledetailComponent } from './profiledetail/profiledetail.component';



const routes: Routes = [
    { path: '', redirectTo: 'profiles', pathMatch: 'full' },
    { path: 'profiles',          component: ProfilelisteComponent },
    { path: 'profiles/:id', component: ProfiledetailComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ProfileRoutingModule {}
