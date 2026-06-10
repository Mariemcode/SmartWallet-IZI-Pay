import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProfilelisteComponent } from './profileliste/profileliste.component';
import { ProfiledetailComponent } from './profiledetail/profiledetail.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ProfileRoutingModule } from './profile.routing';



@NgModule({
  declarations: [
    ProfilelisteComponent,
    ProfiledetailComponent
  ],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    ProfileRoutingModule
  ]
})
export class ProfileModule { }
