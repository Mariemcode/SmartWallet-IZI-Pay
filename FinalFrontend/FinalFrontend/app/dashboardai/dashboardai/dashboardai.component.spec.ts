import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DashboardaiComponent } from './dashboardai.component';

describe('DashboardaiComponent', () => {
  let component: DashboardaiComponent;
  let fixture: ComponentFixture<DashboardaiComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [DashboardaiComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DashboardaiComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
