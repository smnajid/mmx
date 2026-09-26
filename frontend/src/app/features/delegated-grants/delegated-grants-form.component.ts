import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  CurrencySettingsApiService,
  ManagedCurrency,
  NoticePeriodCode,
  TenorCode,
} from '../../core/api/currency-settings-api.service';
import {
  DelegatedGrant,
  DelegatedGrantsApiService,
} from '../../core/api/delegated-grants-api.service';
import {
  Institution,
  InstitutionSettingsApiService,
} from '../../core/api/institution-settings-api.service';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { WorkspaceToggleFieldsetComponent } from '../../shared/workspace-toggle-fieldset.component';

const CLIENT_CODES = ['PAR', 'SIN'];

@Component({
  selector: 'app-delegated-grants-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, WorkspaceToggleFieldsetComponent],
  template: `
    <section class="settings-panel">
      <a routerLink="/settings/delegated-grants" class="settings-back">← Back to grants</a>
      <h1>{{ isNew() ? 'Create grant' : 'Edit grant' }}</h1>

      @if (!trader.isTrader()) {
        <p class="settings-error" role="alert">Grant management is Trader-only.</p>
      } @else {
        @if (error()) {
          <p class="settings-error" role="alert">{{ error() }}</p>
        }

        <form class="settings-form" [formGroup]="form" (ngSubmit)="save()">
          @if (isNew()) {
            <label>
              Hub institution
              <select formControlName="hubInstitutionCode">
                <option value="" disabled>Select…</option>
                @for (i of institutions(); track i.institutionCode) {
                  <option [value]="i.institutionCode">
                    {{ i.institutionCode }} — {{ i.displayName }}
                  </option>
                }
              </select>
            </label>
            <label>
              Client legal entity
              <select formControlName="clientLegalEntityCode">
                @for (c of CLIENT_CODES; track c) {
                  <option [value]="c">{{ c }}</option>
                }
              </select>
            </label>
            <label>
              Currency
              <select formControlName="currency" (change)="onCurrencyChange()">
                <option value="" disabled>Select…</option>
                @for (c of currencies(); track c.code) {
                  <option [value]="c.code">{{ c.code }}</option>
                }
              </select>
            </label>
          } @else {
            <p class="settings-state mono">
              {{ hubCode() }} · {{ clientCode() }} · {{ currencyCode() }}
            </p>
          }

          <app-workspace-toggle-fieldset
            [hubTenors]="hubTenors()"
            [hubNotices]="hubNotices()"
            [selectedTenors]="selectedTenors()"
            [selectedNotices]="selectedNotices()"
            (tenorToggled)="toggleTenor($event.code, $event.checked)"
            (noticeToggled)="toggleNotice($event.code, $event.checked)"
          />

          <div class="actions">
            <button type="submit" [disabled]="form.invalid || saving() || !canSave()">Save</button>
            @if (!isNew() && existing()?.active) {
              <button type="button" class="warn" (click)="deactivate()" [disabled]="saving()">
                Deactivate
              </button>
            }
            @if (!isNew() && existing() && !existing()!.active) {
              <button type="button" class="activate" (click)="reactivate()" [disabled]="saving()">
                Reactivate
              </button>
            }
          </div>
        </form>
      }
    </section>
  `,
})
export class DelegatedGrantsFormComponent implements OnInit {
  protected readonly CLIENT_CODES = CLIENT_CODES;
  protected readonly trader = inject(TraderContextService);

  private readonly api = inject(DelegatedGrantsApiService);
  private readonly institutionApi = inject(InstitutionSettingsApiService);
  private readonly currencyApi = inject(CurrencySettingsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly isNew = signal(true);
  readonly institutions = signal<Institution[]>([]);
  readonly currencies = signal<ManagedCurrency[]>([]);
  readonly hubTenors = signal<TenorCode[]>([]);
  readonly hubNotices = signal<NoticePeriodCode[]>([]);
  readonly selectedTenors = signal<TenorCode[]>([]);
  readonly selectedNotices = signal<NoticePeriodCode[]>([]);
  readonly existing = signal<DelegatedGrant | null>(null);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly hubCode = signal('');
  readonly clientCode = signal('');
  readonly currencyCode = signal('');

  readonly form = this.fb.group({
    hubInstitutionCode: ['', Validators.required],
    clientLegalEntityCode: ['PAR', Validators.required],
    currency: ['', Validators.required],
  });

  readonly canSave = computed(
    () => this.selectedTenors().length > 0 || this.selectedNotices().length > 0
  );

  ngOnInit(): void {
    if (!this.trader.isTrader()) {
      return;
    }
    const hub = this.route.snapshot.paramMap.get('hubInstitutionCode');
    const client = this.route.snapshot.paramMap.get('clientLegalEntityCode');
    const currency = this.route.snapshot.paramMap.get('currency');
    if (hub && client && currency) {
      this.isNew.set(false);
      this.hubCode.set(hub);
      this.clientCode.set(client);
      this.currencyCode.set(currency);
      this.loadExisting(hub, client, currency);
      return;
    }
    this.loadCreateForm();
  }

  onCurrencyChange(): void {
    const code = this.form.value.currency ?? '';
    const match = this.currencies().find((c) => c.code === code);
    if (match) {
      this.applyHubBounds(match);
    }
  }

  toggleTenor(t: TenorCode, checked: boolean): void {
    const next = new Set(this.selectedTenors());
    if (checked) {
      next.add(t);
    } else {
      next.delete(t);
    }
    this.selectedTenors.set([...next]);
  }

  toggleNotice(n: NoticePeriodCode, checked: boolean): void {
    const next = new Set(this.selectedNotices());
    if (checked) {
      next.add(n);
    } else {
      next.delete(n);
    }
    this.selectedNotices.set([...next]);
  }

  save(): void {
    if (!this.canSave()) {
      this.error.set('Enable at least one tenor or notice period within the hub currency bounds');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const userId = this.trader.userId();
    if (this.isNew()) {
      this.api
        .create(userId, {
          hubInstitutionCode: this.form.value.hubInstitutionCode!,
          clientLegalEntityCode: this.form.value.clientLegalEntityCode!,
          currency: this.form.value.currency!,
          enabledTenors: this.selectedTenors(),
          enabledNoticePeriods: this.selectedNotices(),
        })
        .subscribe({
          next: () => {
            this.saving.set(false);
            void this.router.navigate(['/settings/delegated-grants']);
          },
          error: (err) => this.fail(err),
        });
      return;
    }
    this.api
      .update(userId, this.hubCode(), this.clientCode(), this.currencyCode(), {
        enabledTenors: this.selectedTenors(),
        enabledNoticePeriods: this.selectedNotices(),
      })
      .subscribe({
        next: (g) => {
          this.existing.set(g);
          this.saving.set(false);
        },
        error: (err) => this.fail(err),
      });
  }

  deactivate(): void {
    this.saving.set(true);
    this.api
      .deactivate(this.trader.userId(), this.hubCode(), this.clientCode(), this.currencyCode())
      .subscribe({
        next: (g) => {
          this.existing.set(g);
          this.saving.set(false);
        },
        error: (err) => this.fail(err),
      });
  }

  reactivate(): void {
    this.saving.set(true);
    this.api
      .reactivate(this.trader.userId(), this.hubCode(), this.clientCode(), this.currencyCode())
      .subscribe({
        next: (g) => {
          this.existing.set(g);
          this.saving.set(false);
        },
        error: (err) => this.fail(err),
      });
  }

  private loadCreateForm(): void {
    const userId = this.trader.userId();
    forkJoin({
      institutions: this.institutionApi.list(userId),
      currencies: this.currencyApi.list(userId),
    }).subscribe({
      next: ({ institutions, currencies }) => {
        this.institutions.set(institutions.filter((i) => i.active));
        this.currencies.set(currencies.filter((c) => c.active));
      },
      error: (err) => this.fail(err),
    });
  }

  private loadExisting(hub: string, client: string, currency: string): void {
    const userId = this.trader.userId();
    forkJoin({
      currencies: this.currencyApi.list(userId),
      grants: this.api.listHubGrants(userId),
    }).subscribe({
      next: ({ currencies, grants }) => {
        const grant = grants.find(
          (g) =>
            g.hubInstitutionCode === hub &&
            g.clientLegalEntityCode === client &&
            g.currency === currency
        );
        if (!grant) {
          this.error.set('Grant not found');
          return;
        }
        this.existing.set(grant);
        this.selectedTenors.set([...grant.enabledTenors]);
        this.selectedNotices.set([...grant.enabledNoticePeriods]);
        const hubCurrency = currencies.find((c) => c.code === currency);
        if (hubCurrency) {
          this.applyHubBounds(hubCurrency);
        }
      },
      error: (err) => this.fail(err),
    });
  }

  private applyHubBounds(currency: ManagedCurrency): void {
    this.hubTenors.set([...currency.enabledTenors]);
    this.hubNotices.set([...currency.enabledNoticePeriods]);
    this.selectedTenors.update((tenors) => tenors.filter((t) => currency.enabledTenors.includes(t)));
    this.selectedNotices.update((notices) =>
      notices.filter((n) => currency.enabledNoticePeriods.includes(n))
    );
  }

  private fail(err: { error?: { message?: string }; message?: string }): void {
    this.error.set(err?.error?.message ?? err?.message ?? 'Request failed');
    this.saving.set(false);
  }
}
