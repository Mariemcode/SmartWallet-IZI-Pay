import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  showSuccess(message: string): void {
    this.showToast(message, 'success');
  }

  showError(message: string): void {
    this.showToast(message, 'error');
  }

  showInfo(message: string): void {
    this.showToast(message, 'info');
  }

  private showToast(message: string, type: 'success' | 'error' | 'info'): void {
    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      container.style.position = 'fixed';
      container.style.bottom = '20px';
      container.style.right = '20px';
      container.style.zIndex = '9999';
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast-notification toast-${type}`;
    toast.innerText = message;

    toast.style.backgroundColor = type === 'success' ? '#28a745' : type === 'error' ? '#dc3545' : '#17a2b8';
    toast.style.color = 'white';
    toast.style.padding = '12px 20px';
    toast.style.marginBottom = '10px';
    toast.style.borderRadius = '4px';
    toast.style.fontSize = '14px';
    toast.style.boxShadow = '0 2px 5px rgba(0,0,0,0.2)';
    toast.style.animation = 'fadeInOut 3s ease forwards';

    container.appendChild(toast);

    setTimeout(() => {
      toast.remove();
      if (container && container.children.length === 0) {
        container.remove();
      }
    }, 3000);
  }
}