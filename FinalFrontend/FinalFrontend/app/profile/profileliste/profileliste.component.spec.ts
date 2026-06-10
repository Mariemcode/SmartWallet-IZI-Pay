import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProfilelisteComponent } from './profileliste.component';

describe('ProfilelisteComponent', () => {
  let component: ProfilelisteComponent;
  let fixture: ComponentFixture<ProfilelisteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ProfilelisteComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProfilelisteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
