import { Component, inject, OnInit, output, signal } from '@angular/core';
import type { NoticePeriod, Tenor } from '../models/order-creation-payload.model';
import { WizardApiService } from '../services/wizard-api.service';
import { WizardStateService } from '../services/wizard-state.service';

@Component({
  selector: 'mmx-step-tenor-notice-period',
  standalone: true,
  template: `
    <section class="step-tenor-notice-period">
      @if (wizardState.state().contractShortcut) {
        <p class="step-state" data-testid="tenor-skipped">
          Notice period was resolved from the contract. Continuing to counterparty selection.
        </p>
      } @else {
        <h2 class="step-title">{{ heading() }}</h2>

        @if (loading()) {
          <p class="step-state" data-testid="tenor-loading">Loading options…</p>
        } @else if (error()) {
          <div class="step-error" data-testid="tenor-error" role="alert">
            <p>{{ error() }}</p>
            <button type="button" data-testid="tenor-retry" (click)="load()">Retry</button>
          </div>
        } @else if (options().length === 0) {
          <p class="step-state" data-testid="tenor-empty">No options are currently available.</p>
        } @else {
          <div class="choice-grid">
            @for (option of options(); track option) {
              <button
                type="button"
                class="choice-card"
                [attr.data-testid]="optionTestId(option)"
                (click)="select(option)"
              >
                <span class="choice-label">{{ option }}</span>
              </button>
            }
          </div>
        }
      }
    </section>
  `,
  styles: `
    .step-tenor-notice-period {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .step-title {
      margin: 0;
      font-size: 1.125rem;
      font-weight: 600;
    }

    .step-state {
      margin: 0;
      color: #667085;
    }

    .step-error {
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

    .step-error p {
      margin: 0;
    }

    .step-error button {
      padding: 0.375rem 0.75rem;
      border: 1px solid #b42318;
      border-radius: 0.375rem;
      background: #fff;
      color: #b42318;
      cursor: pointer;
    }

    .choice-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(6rem, 1fr));
      gap: 0.75rem;
    }

    .choice-card {
      padding: 1rem;
      border: 1px solid #d0d5dd;
      border-radius: 0.5rem;
      background: #fff;
      cursor: pointer;
      text-align: center;
    }

    .choice-card:hover {
      border-color: #98a2b3;
      background: #f9fafb;
    }

    .choice-label {
      font-weight: 600;
      color: #101828;
    }
  `,
})
export class StepTenorNoticePeriodComponent implements OnInit {
  protected readonly wizardState = inject(WizardStateService);
  private readonly api = inject(WizardApiService);

  readonly stepComplete = output<void>();

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly options = signal<Array<Tenor | NoticePeriod>>([]);
  readonly heading = signal('Choose tenor');

  ngOnInit(): void {
    if (this.wizardState.state().contractShortcut) {
      this.loading.set(false);
      return;
    }
    this.heading.set(
      this.wizardState.state().orderType === 'TERM' ? 'Choose tenor' : 'Choose notice period',
    );
    this.load();
  }

  load(): void {
    const { orderType, currency } = this.wizardState.state();
    if (!orderType || !currency) {
      this.loading.set(false);
      this.error.set('Currency must be selected before choosing tenor or notice period.');
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.options.set([]);

    if (orderType === 'TERM') {
      this.api.listTermTenors(currency).subscribe({
        next: (response) => {
          this.options.set(response.tenors);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.error.set('Unable to load options. Please try again.');
        },
      });
      return;
    }

    this.api.listOnCallNoticePeriods(currency).subscribe({
      next: (response) => {
        this.options.set(response.noticePeriods);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Unable to load options. Please try again.');
      },
    });
  }

  optionTestId(option: Tenor | NoticePeriod): string {
    return this.wizardState.state().orderType === 'TERM' ? `tenor-${option}` : `notice-${option}`;
  }

  select(option: Tenor | NoticePeriod): void {
    if (this.wizardState.state().orderType === 'TERM') {
      this.wizardState.setTenor(option as Tenor);
    } else {
      this.wizardState.setNoticePeriod(option as NoticePeriod);
    }
    this.wizardState.completeAndAdvance();
    this.stepComplete.emit();
  }
}
