import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { RecommendationlistComponent } from './recommendationlist/recommendationlist.component';
import { RecommendationdetailComponent } from './recommendationdetail/recommendationdetail.component';
import { OfferlistComponent } from './offerlist/offerlist.component';
import { OfferdetailComponent } from './offerdetail/offerdetail.component';
import { OfferformComponent } from './offerform/offerform.component';
import { RecommendationformComponent } from './recommendationform/recommendationform.component';

const routes: Routes = [
  { path: '', redirectTo: 'recommendations', pathMatch: 'full' },
  { path: 'recommendations', component: RecommendationlistComponent },
  { path: 'recommendations/:id', component: RecommendationdetailComponent },
  { path: 'recommendationsForm', component: RecommendationformComponent },
  { path: 'recommendations/edit/:id', component: RecommendationformComponent },

  { path: 'offers', component: OfferlistComponent },
  { path: 'offer/:offerCode', component: OfferdetailComponent },
  { path: 'offers/create', component: OfferformComponent }, 
  { path: 'offers/edit/:offerCode', component: OfferformComponent }, 

];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class RecommendationRoutingModule {}
