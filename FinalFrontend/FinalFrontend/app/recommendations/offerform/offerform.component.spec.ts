import { ComponentFixture, TestBed } from '@angular/core/testing';

import { OfferformComponent } from './offerform.component';

describe('OfferformComponent', () => {
  let component: OfferformComponent;
  let fixture: ComponentFixture<OfferformComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [OfferformComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(OfferformComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
