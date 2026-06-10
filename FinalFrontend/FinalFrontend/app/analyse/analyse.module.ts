import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AnalyseRoutingModule } from './analyse.routing';
import { GlobalsummaryComponent } from './globalsummary/globalsummary.component';
import { FormsModule } from '@angular/forms';
import { DepenseComponent } from './depense/depense.component';
import { RevenueComponent } from './revenue/revenue.component';
import { SouscategoryComponent } from './souscategory/souscategory.component';



@NgModule({
  declarations: [
    GlobalsummaryComponent,
    DepenseComponent,
    RevenueComponent,
    SouscategoryComponent
  ],
  imports: [
    CommonModule,
    AnalyseRoutingModule,
    FormsModule
  ]
})
export class AnalyseModule { }
