import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ModelheaderComponent } from './modelheader.component';

describe('ModelheaderComponent', () => {
  let component: ModelheaderComponent;
  let fixture: ComponentFixture<ModelheaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ModelheaderComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ModelheaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
