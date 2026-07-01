import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { TermRateSettingsComponent } from './term-rate-settings.component';
import { TraderContextService } from '../../core/trader/trader-context.service';
import type { TermRate } from '../../core/api/term-rate-settings-api.service';
import type { Institution } from '../../core/api/institution-settings-api.service';

describe('TermRateSettingsComponent', () => {
  let fixture: ComponentFixture<TermRateSettingsComponent>;
  let http: HttpTestingController;
  let confirmSpy: ReturnType<typeof vi.spyOn>;

  const HSBC_INST: Institution = {
    institutionCode: 'HSBC-01',
    displayName: 'HSBC',
    active: true,
  };

  beforeEach(async () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

    await TestBed.configureTestingModule({
      imports: [TermRateSettingsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: TraderContextService,
          useValue: {
            traderId: () => 'trader-test',
            isTrader: () => true,
            isClientRepresentative: () => false,
          },
        },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TermRateSettingsComponent);
    fixture.detectChanges();
    flushInitRequests([], []);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function flushInstitutions(list: Institution[] = []): void {
    http.match((r) => r.url === '/api/v1/settings/institutions').forEach((r) => r.flush(list));
  }

  function flushInitRequests(
    rates: TermRate[],
    days: { tradingDate: string }[] = [],
    institutions: Institution[] = []
  ): void {
    const daysReq = http.match((r) => r.url === '/api/v1/settings/term-rates/days');
    daysReq.forEach((r) => r.flush(days));

    flushInstitutions(institutions);

    http
      .match(
        (r) =>
          r.method === 'GET' &&
          r.url.startsWith('/api/v1/settings/term-rates') &&
          !r.url.includes('/sample') &&
          !r.url.includes('/days') &&
          !r.url.includes('/upload')
      )
      .forEach((r) => r.flush(rates));
  }

  function flushListRequests(rates: TermRate[], institutions: Institution[] = []): void {
    flushInstitutions(institutions);
    http
      .match(
        (r) =>
          r.method === 'GET' &&
          r.url.startsWith('/api/v1/settings/term-rates') &&
          !r.url.includes('/sample') &&
          !r.url.includes('/days') &&
          !r.url.includes('/upload')
      )
      .forEach((r) => r.flush(rates));
  }

  function reviewTree(): HTMLElement {
    return fixture.nativeElement.querySelector('[data-testid="term-rates-review-tree"]');
  }

  function openInstitutionSummaries(): HTMLElement[] {
    return [
      ...reviewTree().querySelectorAll<HTMLElement>('.settings-review-tree__institution > summary'),
    ];
  }

  it('shows lede and prepare, upload, review section headings', () => {
    const text = fixture.nativeElement.textContent ?? '';
    expect(text).toContain('Morning reference sheet');
    expect(text).toContain('Prepare');
    expect(text).toContain('Upload');
    expect(text).toContain('Review');
  });

  it('loads trading days on init and renders chips', () => {
    fixture = TestBed.createComponent(TermRateSettingsComponent);
    fixture.detectChanges();
    flushInitRequests([], [{ tradingDate: '2026-05-31' }, { tradingDate: '2026-05-30' }]);
    fixture.detectChanges();

    const chips = fixture.nativeElement.querySelectorAll('.settings-day-chip');
    expect(chips.length).toBe(2);
    expect(chips[0].textContent?.trim()).toBe('2026-05-31');
  });

  it('chip click changes trading day and reloads rates', () => {
    fixture = TestBed.createComponent(TermRateSettingsComponent);
    fixture.detectChanges();
    flushInitRequests([], [{ tradingDate: '2026-05-31' }, { tradingDate: '2026-05-30' }]);
    fixture.detectChanges();

    const chips = fixture.nativeElement.querySelectorAll('.settings-day-chip');
    (chips[1] as HTMLButtonElement).click();
    fixture.detectChanges();

    flushListRequests(
      [
        {
          tradingDate: '2026-05-30',
          institutionCode: 'HSBC-01',
          currency: 'EUR',
          tenor: '1M',
          rate: 3.25,
          uploadedAt: '2026-05-30T10:00:00Z',
          uploadedBy: 'trader-test',
        },
      ],
      [HSBC_INST]
    );
    fixture.detectChanges();

    expect(fixture.componentInstance.tradingDate()).toBe('2026-05-30');
    expect(reviewTree().textContent).toContain('HSBC');
  });

  it('shows day summary with row count and last upload', () => {
    fixture = TestBed.createComponent(TermRateSettingsComponent);
    fixture.componentInstance.tradingDate.set('2026-05-30');
    fixture.detectChanges();
    flushInitRequests(
      [
        {
          tradingDate: '2026-05-30',
          institutionCode: 'HSBC-01',
          currency: 'EUR',
          tenor: '1M',
          rate: 3.25,
          uploadedAt: '2026-05-30T08:12:00Z',
          uploadedBy: 'trader-test',
        },
        {
          tradingDate: '2026-05-30',
          institutionCode: 'BCI-01',
          currency: 'USD',
          tenor: '1W',
          rate: 4.1,
          uploadedAt: '2026-05-30T10:00:00Z',
          uploadedBy: 'trader-test',
        },
      ],
      []
    );
    fixture.detectChanges();

    const summary = fixture.nativeElement.querySelector('.settings-day-summary');
    expect(summary?.textContent).toContain('2 rate(s)');
    expect(summary?.textContent).toContain('2026-05-30');
  });

  it('empty state offers sample download', () => {
    flushListRequests([]);
    fixture.detectChanges();
    const reviewCard = fixture.nativeElement.querySelector('#review-heading')?.parentElement;
    expect(reviewCard?.textContent).toContain('No rates uploaded');
    expect(reviewCard?.querySelector('button.btn-secondary')).toBeTruthy();
  });

  it('downloads sample CSV from prepare section', () => {
    const prepareCard = fixture.nativeElement.querySelector('#prepare-heading')?.parentElement;
    const downloadBtn: HTMLButtonElement = prepareCard!.querySelector('button.btn-secondary')!;
    downloadBtn.click();
    const req = http.expectOne('/api/v1/settings/term-rates/sample');
    expect(req.request.method).toBe('GET');
    req.flush(new Blob(['tradingDate,institutionCode,currency,tenor,rate\n'], { type: 'text/csv' }));
  });

  it('shows institution display name and code on review header', () => {
    fixture = TestBed.createComponent(TermRateSettingsComponent);
    fixture.detectChanges();
    flushInitRequests(
      [
        {
          tradingDate: '2026-05-30',
          institutionCode: 'HSBC-01',
          currency: 'EUR',
          tenor: '1M',
          rate: 3.25,
          uploadedAt: '2026-05-30T10:00:00Z',
          uploadedBy: 'trader-test',
        },
      ],
      [],
      [HSBC_INST]
    );
    fixture.detectChanges();

    const instSummary = openInstitutionSummaries()[0];
    expect(instSummary.textContent).toContain('HSBC');
    expect(instSummary.querySelector('.settings-review-tree__inst-code')?.textContent?.trim()).toBe('HSBC-01');
  });

  it('falls back to institution code when catalog has no display name', () => {
    fixture = TestBed.createComponent(TermRateSettingsComponent);
    fixture.detectChanges();
    flushInitRequests(
      [
        {
          tradingDate: '2026-05-30',
          institutionCode: 'XX-99',
          currency: 'EUR',
          tenor: '1M',
          rate: 1,
          uploadedAt: '2026-05-30T10:00:00Z',
          uploadedBy: 'trader-test',
        },
      ],
      [],
      []
    );
    fixture.detectChanges();

    const instSummary = openInstitutionSummaries()[0];
    expect(instSummary.querySelector('.settings-review-tree__inst-name')?.textContent?.trim()).toBe('XX-99');
    expect(instSummary.querySelector('.settings-review-tree__inst-code')).toBeNull();
  });

  it('review tree starts collapsed and expand all reveals leaves', () => {
    fixture = TestBed.createComponent(TermRateSettingsComponent);
    fixture.detectChanges();
    flushInitRequests(
      [
        {
          tradingDate: '2026-05-30',
          institutionCode: 'HSBC-01',
          currency: 'EUR',
          tenor: '1M',
          rate: 3.25,
          uploadedAt: '2026-05-30T10:00:00Z',
          uploadedBy: 'trader-test',
        },
      ],
      [],
      [HSBC_INST]
    );
    fixture.detectChanges();

    const instDetails = reviewTree().querySelector(
      '.settings-review-tree__institution'
    ) as HTMLDetailsElement;
    const ccyDetails = reviewTree().querySelector(
      '.settings-review-tree__currency'
    ) as HTMLDetailsElement;
    expect(instDetails.open).toBe(false);
    expect(ccyDetails.open).toBe(false);

    const buttons = [...fixture.nativeElement.querySelectorAll('.settings-review-tree__toolbar button')];
    (buttons[0] as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(instDetails.open).toBe(true);
    expect(reviewTree().textContent).toContain('1M');
    expect(reviewTree().textContent).toContain('3.25');
  });

  it('collapse all hides review leaves', () => {
    fixture = TestBed.createComponent(TermRateSettingsComponent);
    fixture.detectChanges();
    flushInitRequests(
      [
        {
          tradingDate: '2026-05-30',
          institutionCode: 'HSBC-01',
          currency: 'EUR',
          tenor: '1M',
          rate: 3.25,
          uploadedAt: '2026-05-30T10:00:00Z',
          uploadedBy: 'trader-test',
        },
      ],
      [],
      [HSBC_INST]
    );
    fixture.detectChanges();

    const toolbarButtons = fixture.nativeElement.querySelectorAll(
      '.settings-review-tree__toolbar button'
    );
    (toolbarButtons[0] as HTMLButtonElement).click();
    fixture.detectChanges();
    (toolbarButtons[1] as HTMLButtonElement).click();
    fixture.detectChanges();

    const instDetails = reviewTree().querySelector(
      '.settings-review-tree__institution'
    ) as HTMLDetailsElement;
    const ccyDetails = reviewTree().querySelector(
      '.settings-review-tree__currency'
    ) as HTMLDetailsElement;
    expect(instDetails.open).toBe(false);
    expect(ccyDetails.open).toBe(false);
  });

  it('changing trading day resets review tree to collapsed', () => {
    fixture = TestBed.createComponent(TermRateSettingsComponent);
    fixture.detectChanges();
    flushInitRequests(
      [
        {
          tradingDate: '2026-05-31',
          institutionCode: 'HSBC-01',
          currency: 'EUR',
          tenor: '1M',
          rate: 3.25,
          uploadedAt: '2026-05-31T10:00:00Z',
          uploadedBy: 'trader-test',
        },
      ],
      [{ tradingDate: '2026-05-31' }, { tradingDate: '2026-05-30' }],
      [HSBC_INST]
    );
    fixture.detectChanges();

    const toolbarButtons = fixture.nativeElement.querySelectorAll(
      '.settings-review-tree__toolbar button'
    );
    (toolbarButtons[0] as HTMLButtonElement).click();
    fixture.detectChanges();

    const chips = fixture.nativeElement.querySelectorAll('.settings-day-chip');
    (chips[1] as HTMLButtonElement).click();
    fixture.detectChanges();

    flushListRequests(
      [
        {
          tradingDate: '2026-05-30',
          institutionCode: 'HSBC-01',
          currency: 'USD',
          tenor: '1W',
          rate: 4,
          uploadedAt: '2026-05-30T10:00:00Z',
          uploadedBy: 'trader-test',
        },
      ],
      [HSBC_INST]
    );
    fixture.detectChanges();

    const instDetails = reviewTree().querySelector(
      '.settings-review-tree__institution'
    ) as HTMLDetailsElement;
    expect(instDetails.open).toBe(false);
  });

  it('shows rates after successful upload', () => {
    const file = new File(['csv'], 'rates.csv', { type: 'text/csv' });
    fixture.componentInstance.selectedFile.set(file);
    fixture.detectChanges();
    const uploadBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button.btn-primary')!;
    uploadBtn.click();

    const uploadReq = http.expectOne('/api/v1/settings/term-rates/upload');
    expect(uploadReq.request.method).toBe('POST');
    uploadReq.flush({
      tradingDate: '2026-05-30',
      rowCount: 1,
      uploadedAt: '2026-05-30T10:00:00Z',
    });

    const daysReq = http.expectOne('/api/v1/settings/term-rates/days');
    daysReq.flush([{ tradingDate: '2026-05-30' }]);

    flushListRequests(
      [
        {
          tradingDate: '2026-05-30',
          institutionCode: 'HSBC-01',
          currency: 'EUR',
          tenor: '1M',
          rate: 3.25,
          uploadedAt: '2026-05-30T10:00:00Z',
          uploadedBy: 'trader-test',
        },
      ],
      [HSBC_INST]
    );

    fixture.detectChanges();
    expect(fixture.componentInstance.rates()).toHaveLength(1);
    expect(reviewTree().textContent).toContain('HSBC');
  });

  it('prompts replace-day confirm when rates exist; cancel skips upload', () => {
    fixture.componentInstance.rates.set([
      {
        tradingDate: '2026-05-30',
        institutionCode: 'HSBC-01',
        currency: 'EUR',
        tenor: '1M',
        rate: 3.25,
        uploadedAt: '2026-05-30T10:00:00Z',
        uploadedBy: 'trader-test',
      },
    ]);
    confirmSpy.mockReturnValue(false);
    const file = new File(['csv'], 'rates.csv', { type: 'text/csv' });
    fixture.componentInstance.selectedFile.set(file);
    fixture.detectChanges();

    const uploadBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button.btn-primary')!;
    uploadBtn.click();

    expect(confirmSpy).toHaveBeenCalled();
    http.expectNone('/api/v1/settings/term-rates/upload');
  });

  it('proceeds with upload when replace-day confirm accepted', () => {
    fixture.componentInstance.rates.set([
      {
        tradingDate: '2026-05-30',
        institutionCode: 'HSBC-01',
        currency: 'EUR',
        tenor: '1M',
        rate: 3.25,
        uploadedAt: '2026-05-30T10:00:00Z',
        uploadedBy: 'trader-test',
      },
    ]);
    confirmSpy.mockReturnValue(true);
    const file = new File(['csv'], 'rates.csv', { type: 'text/csv' });
    fixture.componentInstance.selectedFile.set(file);
    fixture.detectChanges();

    const uploadBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button.btn-primary')!;
    uploadBtn.click();

    expect(confirmSpy).toHaveBeenCalled();
    const uploadReq = http.expectOne('/api/v1/settings/term-rates/upload');
    uploadReq.flush({
      tradingDate: '2026-05-30',
      rowCount: 1,
      uploadedAt: '2026-05-30T10:00:00Z',
    });
    http.expectOne('/api/v1/settings/term-rates/days').flush([{ tradingDate: '2026-05-30' }]);
    flushListRequests([], []);
  });

  it('displays row errors from failed upload', () => {
    const file = new File(['csv'], 'bad.csv', { type: 'text/csv' });
    fixture.componentInstance.selectedFile.set(file);
    fixture.detectChanges();
    const uploadBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button.btn-primary')!;
    uploadBtn.click();

    const uploadReq = http.expectOne('/api/v1/settings/term-rates/upload');
    uploadReq.flush(
      {
        error: 'TERM_RATE_INGEST_ERROR',
        message: 'validation failed',
        errors: [{ line: 2, field: 'institutionCode', message: 'Institution not found' }],
      },
      { status: 400, statusText: 'Bad Request' }
    );

    fixture.detectChanges();
    expect(fixture.componentInstance.rowErrors()).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Institution not found');
  });
});

describe('TermRateSettingsComponent ClientRepresentative', () => {
  it('hides upload controls for ClientRepresentative', async () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    await TestBed.configureTestingModule({
      imports: [TermRateSettingsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: TraderContextService,
          useValue: {
            traderId: () => 'trader-test',
            isTrader: () => false,
            isClientRepresentative: () => true,
          },
        },
      ],
    }).compileComponents();

    const clientHttp = TestBed.inject(HttpTestingController);
    const clientFixture = TestBed.createComponent(TermRateSettingsComponent);
    clientFixture.detectChanges();
    clientHttp.match((r) => r.url === '/api/v1/settings/term-rates/days').forEach((r) => r.flush([]));
    clientHttp.match((r) => r.url === '/api/v1/settings/institutions').forEach((r) => r.flush([]));
    clientHttp
      .match(
        (r) =>
          r.method === 'GET' &&
          r.url.startsWith('/api/v1/settings/term-rates') &&
          !r.url.includes('/sample') &&
          !r.url.includes('/days') &&
          !r.url.includes('/upload')
      )
      .forEach((r) => r.flush([]));
    await clientFixture.whenStable();
    clientFixture.detectChanges();

    const el = clientFixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Read-only view');
    expect(el.querySelector('button.btn-primary')).toBeNull();
    expect(el.querySelector('input[type="file"]')).toBeNull();
    clientHttp.verify();
    vi.restoreAllMocks();
  });
});
