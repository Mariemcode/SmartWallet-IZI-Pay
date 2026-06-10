import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DepenseComponent } from './depense.component';

describe('DepenseComponent', () => {
  let component: DepenseComponent;
  let fixture: ComponentFixture<DepenseComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [DepenseComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DepenseComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
