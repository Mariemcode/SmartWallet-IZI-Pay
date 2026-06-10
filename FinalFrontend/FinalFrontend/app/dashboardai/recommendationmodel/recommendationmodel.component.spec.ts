import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RecommendationmodelComponent } from './recommendationmodel.component';

describe('RecommendationmodelComponent', () => {
  let component: RecommendationmodelComponent;
  let fixture: ComponentFixture<RecommendationmodelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [RecommendationmodelComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RecommendationmodelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
