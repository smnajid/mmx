import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import type { NoticePeriodCode, TenorCode } from '../../core/api/currency-settings-api.service';
import {
  ClientEnablement,
  Institution,
  InstitutionSettingsApiService,
} from '../../core/api/institution-settings-api.service';
import { ALL_NOTICES, ALL_TENORS } from '../../core/api/workspace-codes';
import { TraderContextService } from '../../core/trader/trader-context.service';

/** Per-currency working copy of the client enablement being edited. */
interface EnablementDraft {
  grant: ClientEnablement;
  tenors: TenorCode[];
  notices: NoticePeriodCode[];
}

@Component({
  selector: 'app-institution-settings-detail',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="settings-panel">
      <a routerLink="/settings/institutions" class="settings-back">← Back to list</a>

      @if (loading()) {
        <p class="settings-state">Loading…</p>
      } @else if (institution(); as i) {
        <header class="settings-header">
          <h1>{{ i.displayName }}</h1>
        </header>

        <dl class="detail-dl">
          <dt>Institution code</dt>
          <dd class="mono">{{ i.institutionCode }}</dd>
          <dt>Status</dt>
          <dd>
            <span
              class="status-badge"
              [class.status-badge--active]="!i.closedToNewBusiness"
              [class.status-badge--inactive]="i.closedToNewBusiness"
            >
              {{ i.closedToNewBusiness ? 'Closed to new business' : 'Open' }}
            </span>
          </dd>
          @if (i.hubInstitutionCode) {
            <dt>Hub institution</dt>
            <dd class="mono">{{ i.hubInstitutionCode }} via {{ i.hubLegalEntityCode }}</dd>
          }
        </dl>

        @if (error()) {
          <p class="settings-error" role="alert">{{ error() }}</p>
        }
        @if (notice()) {
          <p class="settings-state" role="status">{{ notice() }}</p>
        }

        <section class="settings-card">
          <h2 class="settings-card__title">Counterparty accounts</h2>
          <form class="settings-form" [formGroup]="accountsForm" (ngSubmit)="saveAccounts()">
            <label>
              Term counterparty account
              <input formControlName="termCounterpartyAccount" maxlength="34" />
            </label>
            <label>
              OnCall counterparty account
              <input formControlName="onCallCounterpartyAccount" maxlength="34" />
            </label>
            <div class="actions">
              <button type="submit" class="activate" [disabled]="accountsForm.invalid || acting()">
                Save accounts
              </button>
            </div>
          </form>
        </section>

        @if (trader.isClientRepresentative()) {
          @for (d of drafts(); track d.grant.currency) {
            <section class="settings-card" [attr.data-testid]="'enablement-' + d.grant.currency">
              <h2 class="settings-card__title">Client enablement · {{ d.grant.currency }}</h2>
              <p class="settings-hint">
                Switch on the tenors and notice periods you trade. Only what the hub grants can be
                switched on, and each OrderType needs its counterparty account.
              </p>
              <fieldset>
                <legend>Term tenors</legend>
                @for (t of ALL_TENORS; track t) {
                  <label class="chk">
                    <input
                      type="checkbox"
                      [attr.data-code]="t"
                      [checked]="d.tenors.includes(t)"
                      [disabled]="tenorLocked(d, t)"
                      (change)="toggleTenor(d, t)"
                    />
                    {{ t }}
                    @if (d.tenors.includes(t) && !d.grant.grantedTenors.includes(t)) {
                      <span class="not-granted" [attr.data-testid]="'not-granted-' + t">
                        enabled, not granted
                      </span>
                    }
                  </label>
                }
              </fieldset>
              <fieldset>
                <legend>OnCall notice periods</legend>
                @for (n of ALL_NOTICES; track n) {
                  <label class="chk">
                    <input
                      type="checkbox"
                      [attr.data-code]="n"
                      [checked]="d.notices.includes(n)"
                      [disabled]="noticeLocked(d, n)"
                      (change)="toggleNotice(d, n)"
                    />
                    {{ n }}
                    @if (d.notices.includes(n) && !d.grant.grantedNoticePeriods.includes(n)) {
                      <span class="not-granted" [attr.data-testid]="'not-granted-' + n">
                        enabled, not granted
                      </span>
                    }
                  </label>
                }
              </fieldset>
              <div class="actions">
                <button
                  type="button"
                  class="activate"
                  (click)="saveEnablement(d)"
                  [disabled]="acting()"
                >
                  Save {{ d.grant.currency }}
                </button>
              </div>
            </section>
          }
        }

        <div class="actions">
          @if (i.closedToNewBusiness) {
            <button type="button" class="activate" (click)="activate()" [disabled]="acting()">
              {{ trader.isClientRepresentative() ? 'Re-onboard' : 'Reactivate' }}
            </button>
          } @else {
            <button type="button" class="warn" (click)="deactivate()" [disabled]="acting()">
              {{ trader.isClientRepresentative() ? 'Offboard' : 'Deactivate' }}
            </button>
          }
        </div>
      }
    </section>
  `,
  styles: `
    .detail-dl {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 0.35rem 1.25rem;
      margin: 0 0 1.25rem;
      font-size: 0.9rem;
    }

    .detail-dl dt {
      font-family: var(--font-mono);
      font-size: 0.65rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--mmx-text-muted);
    }

    .detail-dl dd {
      margin: 0;
    }

    .mono {
      font-family: var(--font-mono);
    }

    fieldset {
      border: 1px solid var(--mmx-border);
      border-radius: 4px;
      padding: 0.75rem 1rem;
      margin: 0 0 1rem;
    }

    legend {
      font-family: var(--font-mono);
      font-size: 0.65rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--mmx-text-muted);
      padding: 0 0.25rem;
    }

    .chk {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      margin-right: 0.85rem;
      font-size: 0.85rem;
    }

    .not-granted {
      font-family: var(--font-mono);
      font-size: 0.65rem;
      color: #fcd34d;
    }

    .actions {
      display: flex;
      gap: 0.75rem;
    }

    .actions button {
      font-family: var(--font-mono);
      font-size: 0.68rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      padding: 0.4rem 0.9rem;
      border-radius: 4px;
      cursor: pointer;
    }

    .actions button.warn {
      background: rgba(180, 83, 9, 0.2);
      border: 1px solid rgba(251, 191, 36, 0.45);
      color: #fcd34d;
    }

    .actions button.activate {
      background: var(--mmx-accent-dim);
      border: 1px solid var(--mmx-accent);
      color: var(--mmx-accent);
    }
  `,
})
export class InstitutionSettingsDetailComponent implements OnInit {
  protected readonly trader = inject(TraderContextService);
  protected readonly ALL_TENORS = ALL_TENORS;
  protected readonly ALL_NOTICES = ALL_NOTICES;

  private readonly api = inject(InstitutionSettingsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly accountsForm = this.fb.group({
    termCounterpartyAccount: ['', Validators.maxLength(34)],
    onCallCounterpartyAccount: ['', Validators.maxLength(34)],
  });

  readonly institution = signal<Institution | null>(null);
  readonly drafts = signal<EnablementDraft[]>([]);
  readonly loading = signal(true);
  readonly acting = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('institutionCode') ?? '';
    this.api.get(this.trader.userId(), code).subscribe({
      next: (inst) => {
        this.show(inst);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message ?? 'Failed to load institution');
        this.loading.set(false);
      },
    });
  }

  /** A tenor can be switched off at any time; switching on needs the grant and the Term account. */
  tenorLocked(d: EnablementDraft, t: TenorCode): boolean {
    if (d.tenors.includes(t)) {
      return false;
    }
    return !d.grant.grantedTenors.includes(t) || !this.institution()?.termCounterpartyAccount;
  }

  noticeLocked(d: EnablementDraft, n: NoticePeriodCode): boolean {
    if (d.notices.includes(n)) {
      return false;
    }
    return (
      !d.grant.grantedNoticePeriods.includes(n) || !this.institution()?.onCallCounterpartyAccount
    );
  }

  toggleTenor(d: EnablementDraft, t: TenorCode): void {
    const tenors = d.tenors.includes(t)
      ? d.tenors.filter((x) => x !== t)
      : ALL_TENORS.filter((x) => x === t || d.tenors.includes(x));
    this.replaceDraft({ ...d, tenors });
  }

  toggleNotice(d: EnablementDraft, n: NoticePeriodCode): void {
    const notices = d.notices.includes(n)
      ? d.notices.filter((x) => x !== n)
      : ALL_NOTICES.filter((x) => x === n || d.notices.includes(x));
    this.replaceDraft({ ...d, notices });
  }

  saveEnablement(d: EnablementDraft): void {
    const i = this.institution();
    if (!i) {
      return;
    }
    this.run(
      () =>
        this.api.updateClientEnablement(this.trader.userId(), i.institutionCode, d.grant.currency, {
          enabledTenors: d.tenors,
          enabledNoticePeriods: d.notices,
        }),
      `Client enablement saved for ${d.grant.currency}`,
    );
  }

  saveAccounts(): void {
    const i = this.institution();
    if (!i || this.accountsForm.invalid) {
      return;
    }
    const term = (this.accountsForm.value.termCounterpartyAccount ?? '').trim();
    const onCall = (this.accountsForm.value.onCallCounterpartyAccount ?? '').trim();
    this.run(
      () =>
        this.api.updateCounterpartyAccounts(this.trader.userId(), i.institutionCode, {
          termCounterpartyAccount: term || null,
          onCallCounterpartyAccount: onCall || null,
        }),
      'Counterparty accounts saved',
    );
  }

  deactivate(): void {
    const i = this.institution();
    if (!i) {
      return;
    }
    this.run(() => this.api.deactivate(this.trader.userId(), i.institutionCode));
  }

  activate(): void {
    const i = this.institution();
    if (!i) {
      return;
    }
    this.run(() => this.api.activate(this.trader.userId(), i.institutionCode));
  }

  private show(inst: Institution): void {
    this.institution.set(inst);
    this.accountsForm.reset({
      termCounterpartyAccount: inst.termCounterpartyAccount ?? '',
      onCallCounterpartyAccount: inst.onCallCounterpartyAccount ?? '',
    });
    this.drafts.set(
      (inst.enablements ?? []).map((grant) => ({
        grant,
        tenors: [...grant.enabledTenors],
        notices: [...grant.enabledNoticePeriods],
      })),
    );
  }

  private replaceDraft(next: EnablementDraft): void {
    this.drafts.update((all) =>
      all.map((d) => (d.grant.currency === next.grant.currency ? next : d)),
    );
  }

  private run(call: () => Observable<Institution>, successNotice: string | null = null): void {
    this.acting.set(true);
    this.error.set(null);
    this.notice.set(null);
    call().subscribe({
      next: (updated) => {
        this.show(updated);
        this.notice.set(successNotice);
        this.acting.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Update failed');
        this.acting.set(false);
      },
    });
  }
}
