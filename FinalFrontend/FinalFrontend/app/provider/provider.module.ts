import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProviderlistComponent } from './providerlist/providerlist.component';
import { ProviderdetailsComponent } from './providerdetails/providerdetails.component';
import { ProviderRoutingModule } from './provider.routing';
import { FormsModule } from '@angular/forms';



@NgModule({
  declarations: [
    ProviderlistComponent,
    ProviderdetailsComponent
  ],
  imports: [
    CommonModule,
    ProviderRoutingModule,
    FormsModule
  ]
})
export class ProviderModule { }
