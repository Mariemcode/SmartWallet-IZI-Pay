import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RecommendationlistComponent } from './recommendationlist.component';

describe('RecommendationlistComponent', () => {
  let component: RecommendationlistComponent;
  let fixture: ComponentFixture<RecommendationlistComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [RecommendationlistComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RecommendationlistComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
