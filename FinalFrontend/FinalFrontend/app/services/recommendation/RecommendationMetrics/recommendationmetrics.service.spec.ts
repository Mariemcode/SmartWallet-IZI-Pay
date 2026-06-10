import { TestBed } from '@angular/core/testing';

import { RecommendationmetricsService } from './recommendationmetrics.service';

describe('RecommendationmetricsService', () => {
  let service: RecommendationmetricsService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(RecommendationmetricsService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
