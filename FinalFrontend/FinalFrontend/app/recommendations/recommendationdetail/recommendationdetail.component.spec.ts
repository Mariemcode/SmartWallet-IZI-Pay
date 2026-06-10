import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RecommendationdetailComponent } from './recommendationdetail.component';

describe('RecommendationdetailComponent', () => {
  let component: RecommendationdetailComponent;
  let fixture: ComponentFixture<RecommendationdetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [RecommendationdetailComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RecommendationdetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
