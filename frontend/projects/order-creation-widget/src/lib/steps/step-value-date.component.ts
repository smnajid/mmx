import { Component, inject, OnInit, output } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { WizardStateService } from '../services/wizard-state.service';
import { isOnOrAfterMinSettlementDate, minSettlementDate } from '../utils/settlement-date';

@Component({
  selector: 'mmx-step-value-date',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <section class="step-value-date">
      <h2 class="step-title">Value date</h2>

      <form [formGroup]="form" (ngSubmit)="submit()">
        <label class="field">
          <span>Value date</span>
          <input
            type="date"
            formControlName="valueDate"
            [min]="minValueDate"
            data-testid="value-date-input"
          />
          @if (form.controls.valueDate.touched && form.controls.valueDate.hasError('minSettlement')) {
            <span class="field-error" data-testid="value-date-error">
              Value date must be at least two calendar days from today.
            </span>
          }
        </label>

        <button type="submit" data-testid="value-date-continue">Continue</button>
      </form>
    </section>
  `,
  styles: `
    .step-value-date {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .step-title {
      margin: 0;
      font-size: 1.125rem;
      font-weight: 600;
    }

    form {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
      font-size: 0.875rem;
      color: #344054;
    }

    .field input {
      padding: 0.5rem 0.75rem;
      border: 1px solid #d0d5dd;
      border-radius: 0.375rem;
    }

    .field-error {
      color: #b42318;
      font-size: 0.8125rem;
    }

    button[type='submit'] {
      align-self: flex-start;
      padding: 0.5rem 1rem;
      border: none;
      border-radius: 0.375rem;
      background: #155eef;
      color: #fff;
      cursor: pointer;
    }
  `,
})
export class StepValueDateComponent implements OnInit {
  private readonly wizardState = inject(WizardStateService);
  private readonly fb = inject(FormBuilder);

  readonly stepComplete = output<void>();

  readonly minValueDate = minSettlementDate();

  readonly form = this.fb.nonNullable.group({
    valueDate: ['', Validators.required],
  });

  ngOnInit(): void {
    const state = this.wizardState.state();
    this.form.controls.valueDate.setValidators([
      Validators.required,
      (control) =>
        control.value && isOnOrAfterMinSettlementDate(control.value)
          ? null
          : { minSettlement: true },
    ]);
    this.form.patchValue({
      valueDate: state.valueDate ?? this.minValueDate,
    });
  }

  submit(): void {
    this.form.updateValueAndValidity();
    this.form.markAllAsTouched();

    if (this.form.invalid) {
      return;
    }

    const { valueDate } = this.form.getRawValue();
    this.wizardState.setValueDate(valueDate);
    this.wizardState.completeAndAdvance();
    this.stepComplete.emit();
  }
}
