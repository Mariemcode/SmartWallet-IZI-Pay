import { ComponentFixture, TestBed } from '@angular/core/testing';

import { OfferdetailComponent } from './offerdetail.component';

describe('OfferdetailComponent', () => {
  let component: OfferdetailComponent;
  let fixture: ComponentFixture<OfferdetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [OfferdetailComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(OfferdetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
