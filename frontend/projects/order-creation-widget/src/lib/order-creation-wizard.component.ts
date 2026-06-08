import { Component, inject, input, OnInit, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import type { OrderCreationPayload, OrderType } from './models/order-creation-payload.model';
import { WizardApiService } from './services/wizard-api.service';
import { WizardHostConfigService } from './services/wizard-host-config.service';
import { WizardStateService } from './services/wizard-state.service';
import { WizardErrorComponent } from './shared/wizard-error.component';
import { WizardLoadingComponent } from './shared/wizard-loading.component';
import { ORDER_CREATION_API_BASE_URL } from './tokens/order-creation-api-base-url.token';
import { WizardShellComponent } from './wizard-shell/wizard-shell.component';

@Component({
  selector: 'mmx-order-creation-wizard',
  standalone: true,
  imports: [WizardShellComponent, WizardLoadingComponent, WizardErrorComponent],
  providers: [WizardHostConfigService, WizardStateService, WizardApiService],
  host: {
    '[class.mmx-order-creation-wizard]': 'true',
    '[class.theme-dark]': 'theme() === "dark"',
    '[class.theme-light]': 'theme() !== "dark"',
  },
  template: `
    @if (configError()) {
      <div class="wizard-config-error" data-testid="wizard-config-error" role="alert">
        {{ configError() }}
      </div>
    } @else if (contractLoading()) {
      <mmx-wizard-loading />
    } @else if (contractError()) {
      <div class="wizard-contract-error" data-testid="wizard-contract-error">
        <mmx-wizard-error [message]="contractError()!" (retry)="resolveContractShortcut()" />
        <button type="button" data-testid="wizard-start-fresh" (click)="startFresh()">
          Start from scratch
        </button>
      </div>
    } @else {
      <mmx-wizard-shell
        [portfolioNumber]="portfolioNumber()"
        (orderReady)="orderReady.emit($event)"
        (cancelled)="cancelled.emit()"
      />
    }
  `,
  styles: `
    :host {
      display: block;
      width: 100%;
      min-height: 0;
    }

    .wizard-config-error,
    .wizard-contract-error {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      padding: 1rem;
      border: 1px solid #fda29b;
      border-radius: 0.5rem;
      background: #fef3f2;
      color: #b42318;
    }

    .wizard-contract-error button[data-testid='wizard-start-fresh'] {
      align-self: flex-start;
      padding: 0.5rem 0.75rem;
      border: 1px solid #b42318;
      border-radius: 0.375rem;
      background: #fff;
      color: #b42318;
      cursor: pointer;
    }
  `,
})
export class OrderCreationWizardComponent implements OnInit {
  private readonly wizardState = inject(WizardStateService);
  private readonly api = inject(WizardApiService);
  private readonly hostConfig = inject(WizardHostConfigService);

  readonly apiBaseUrl = input<string>('');
  readonly portfolioNumber = input<string>('');
  readonly orderType = input<OrderType | undefined>(undefined);
  readonly contractNumber = input<string | undefined>(undefined);
  readonly theme = input<'light' | 'dark'>('light');

  readonly orderReady = output<OrderCreationPayload>();
  readonly cancelled = output<void>();

  readonly configError = signal<string | null>(null);
  readonly contractLoading = signal(false);
  readonly contractError = signal<string | null>(null);

  private skipContractResolution = false;
  private initialized = false;

  ngOnInit(): void {
    if (!this.portfolioNumber().trim()) {
      this.configError.set('portfolioNumber is required.');
      return;
    }
    this.hostConfig.apiBaseUrl = this.apiBaseUrl().trim();
    this.initializeWizard();
  }

  resolveContractShortcut(): void {
    const contractNumber = this.contractNumber()?.trim();
    if (!contractNumber) {
      return;
    }
    this.loadContractShortcut(contractNumber);
  }

  startFresh(): void {
    this.skipContractResolution = true;
    this.contractError.set(null);
    this.contractLoading.set(false);
    this.initializeWizardState();
  }

  private initializeWizard(): void {
    if (this.configError()) {
      return;
    }
    if (this.initialized) {
      return;
    }

    const contractNumber = this.contractNumber()?.trim();
    if (contractNumber && !this.skipContractResolution) {
      this.loadContractShortcut(contractNumber);
      return;
    }

    this.initializeWizardState();
    this.initialized = true;
  }

  private initializeWizardState(): void {
    const orderType = this.orderType();
    if (orderType) {
      this.wizardState.initialize({ orderType, skipOrderType: true });
      return;
    }
    this.wizardState.initialize();
  }

  private loadContractShortcut(contractNumber: string): void {
    this.contractLoading.set(true);
    this.contractError.set(null);

    this.api.getOnCallContractInfo(contractNumber).subscribe({
      next: (response) => {
        this.wizardState.applyContractShortcut(
          response.currency,
          response.noticePeriod,
          contractNumber,
        );
        this.contractLoading.set(false);
        this.initialized = true;
      },
      error: (error: HttpErrorResponse) => {
        this.contractLoading.set(false);
        if (error.status === 404) {
          this.contractError.set('Contract not found');
          return;
        }
        this.contractError.set('Unable to resolve contract. Please try again.');
      },
    });
  }
}
