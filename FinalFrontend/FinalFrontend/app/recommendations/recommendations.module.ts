import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RecommendationlistComponent } from './recommendationlist/recommendationlist.component';
import { RecommendationRoutingModule } from './recommendation.routing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RecommendationdetailComponent } from './recommendationdetail/recommendationdetail.component';
import { OfferlistComponent } from './offerlist/offerlist.component';
import { OfferdetailComponent } from './offerdetail/offerdetail.component';
import { OfferformComponent } from './offerform/offerform.component';
import { RecommendationformComponent } from './recommendationform/recommendationform.component';
import { MaxPipe, MedianPipe, MinPipe, StdDevPipe } from '../services/recommendation/RecommendationMetrics/stats.pipes';



@NgModule({
  declarations: [
    RecommendationlistComponent,
    RecommendationdetailComponent,
    OfferlistComponent,
    OfferdetailComponent,
    OfferformComponent,
    RecommendationformComponent,
    MinPipe, MaxPipe, MedianPipe, StdDevPipe
    
  ],
  imports: [
    CommonModule,
    RecommendationRoutingModule,
    FormsModule,
    ReactiveFormsModule
  ]
})
export class RecommendationsModule { }
