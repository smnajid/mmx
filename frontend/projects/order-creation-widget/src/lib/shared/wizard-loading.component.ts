import { Component } from '@angular/core';

@Component({
  selector: 'mmx-wizard-loading',
  standalone: true,
  template: `
    <div class="wizard-loading" data-testid="wizard-loading" aria-live="polite">
      <span class="wizard-loading-spinner" aria-hidden="true"></span>
      <span>Loading…</span>
    </div>
  `,
  styles: `
    .wizard-loading {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      color: #667085;
      padding: 1rem 0;
    }

    .wizard-loading-spinner {
      width: 1rem;
      height: 1rem;
      border: 2px solid #d0d5dd;
      border-top-color: #155eef;
      border-radius: 50%;
      animation: wizard-spin 0.8s linear infinite;
    }

    @keyframes wizard-spin {
      to {
        transform: rotate(360deg);
      }
    }
  `,
})
export class WizardLoadingComponent {}
