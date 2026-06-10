import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ClientlistComponent } from './clientlist/clientlist.component';
import { ClientdetailComponent } from './clientdetail/clientdetail.component';
import { ClientRoutingModule } from './client.routing';
import { FormsModule } from '@angular/forms';



@NgModule({
  declarations: [
    ClientlistComponent,
    ClientdetailComponent
  ],
  imports: [
    CommonModule,
    ClientRoutingModule,
    FormsModule
  ]
})
export class ClientModule { }
