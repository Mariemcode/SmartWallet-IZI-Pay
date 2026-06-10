import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GlobalsummaryComponent } from './globalsummary.component';

describe('GlobalsummaryComponent', () => {
  let component: GlobalsummaryComponent;
  let fixture: ComponentFixture<GlobalsummaryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [GlobalsummaryComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GlobalsummaryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
