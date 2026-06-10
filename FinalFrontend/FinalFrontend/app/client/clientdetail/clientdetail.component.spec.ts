import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClientdetailComponent } from './clientdetail.component';

describe('ClientdetailComponent', () => {
  let component: ClientdetailComponent;
  let fixture: ComponentFixture<ClientdetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ClientdetailComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ClientdetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
