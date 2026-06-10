import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RetrainMlComponent } from './retrain-ml.component';

describe('RetrainMlComponent', () => {
  let component: RetrainMlComponent;
  let fixture: ComponentFixture<RetrainMlComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RetrainMlComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(RetrainMlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
