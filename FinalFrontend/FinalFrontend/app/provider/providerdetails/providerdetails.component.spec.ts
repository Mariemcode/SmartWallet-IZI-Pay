import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProviderdetailsComponent } from './providerdetails.component';

describe('ProviderdetailsComponent', () => {
  let component: ProviderdetailsComponent;
  let fixture: ComponentFixture<ProviderdetailsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ProviderdetailsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProviderdetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
