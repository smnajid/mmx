import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { TermRateSettingsComponent } from './term-rate-settings.component';
import { TraderContextService } from '../../core/trader/trader-context.service';
import type { TermRate } from '../../core/api/term-rate-settings-api.service';

describe('TermRateSettingsComponent', () => {
  let fixture: ComponentFixture<TermRateSettingsComponent>;
  let http: HttpTestingController;
  let confirmSpy: ReturnType<typeof vi.spyOn>;

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
        { provide: TraderContextService, useValue: { traderId: () => 'trader-test' } },
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

  function flushInitRequests(rates: TermRate[], days: { tradingDate: string }[] = []): void {
    const daysReq = http.match((r) => r.url === '/api/v1/settings/term-rates/days');
    daysReq.forEach((r) => r.flush(days));

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

  function flushListRequests(rates: TermRate[]): void {
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

    flushListRequests([
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
    fixture.detectChanges();

    expect(fixture.componentInstance.tradingDate()).toBe('2026-05-30');
    expect(fixture.nativeElement.textContent).toContain('HSBC-01');
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

    flushListRequests([
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

    fixture.detectChanges();
    expect(fixture.componentInstance.rates()).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('HSBC-01');
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
    flushListRequests([]);
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
