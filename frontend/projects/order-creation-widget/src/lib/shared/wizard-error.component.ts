import { Component, input, output } from '@angular/core';

@Component({
  selector: 'mmx-wizard-error',
  standalone: true,
  template: `
    <div class="wizard-error" role="alert" data-testid="wizard-error">
      <p>{{ message() }}</p>
      <button type="button" data-testid="wizard-error-retry" (click)="retry.emit()">Retry</button>
    </div>
  `,
  styles: `
    .wizard-error {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 0.75rem;
      padding: 0.75rem 1rem;
      border: 1px solid #fda29b;
      border-radius: 0.5rem;
      background: #fef3f2;
      color: #b42318;
    }

    .wizard-error p {
      margin: 0;
    }

    .wizard-error button {
      padding: 0.375rem 0.75rem;
      border: 1px solid #b42318;
      border-radius: 0.375rem;
      background: #fff;
      color: #b42318;
      cursor: pointer;
    }
  `,
})
export class WizardErrorComponent {
  readonly message = input.required<string>();
  readonly retry = output<void>();
}
