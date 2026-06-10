import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-footer',
  templateUrl: './footer.component.html',
  styleUrl: './footer.component.css'
})
export class FooterComponent implements OnInit, OnDestroy {

  currentTime = '';
  currentYear = new Date().getFullYear();

  private clockInterval?: ReturnType<typeof setInterval>;

  constructor(private router: Router) {}

  ngOnInit(): void {
    this.updateTime();
    this.clockInterval = setInterval(() => this.updateTime(), 1000);
  }

  ngOnDestroy(): void {
    clearInterval(this.clockInterval);
  }

  private updateTime(): void {
    this.currentTime = new Date().toLocaleTimeString('fr-FR', {
      hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
  }

  go(path: string): void {
    this.router.navigate([path]);
  }
}