import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';
import { AdminAiDashboardComponent } from './dashboard/adminai-dashboard.component';

const routes: Routes = [
  { path: '', component: AdminAiDashboardComponent },
];

@NgModule({
  declarations: [AdminAiDashboardComponent],
  imports: [
    CommonModule,
    FormsModule,
    RouterModule.forChild(routes),
  ],
})
export class AdminAiModule {}
