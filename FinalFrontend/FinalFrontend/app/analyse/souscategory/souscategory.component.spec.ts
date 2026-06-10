import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SouscategoryComponent } from './souscategory.component';

describe('SouscategoryComponent', () => {
  let component: SouscategoryComponent;
  let fixture: ComponentFixture<SouscategoryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [SouscategoryComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SouscategoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
