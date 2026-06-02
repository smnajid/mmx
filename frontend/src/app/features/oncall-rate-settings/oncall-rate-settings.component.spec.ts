import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { OnCallRateSettingsComponent } from './oncall-rate-settings.component';
import { TraderContextService } from '../../core/trader/trader-context.service';
import type { OnCallRateSegment } from '../../core/api/oncall-rate-settings-api.service';
import type { Institution } from '../../core/api/institution-settings-api.service';
import { OPEN_END_SENTINEL } from './group-oncall-rate-segments-for-review';

describe('OnCallRateSettingsComponent', () => {
  let fixture: ComponentFixture<OnCallRateSettingsComponent>;
  let http: HttpTestingController;

  const HSBC: Institution = { institutionCode: 'HSBC-01', displayName: 'HSBC', active: true };
  const CITI: Institution = { institutionCode: 'CITI-01', displayName: 'Citi', active: true };

  const pendingSegment: OnCallRateSegment = {
    segmentId: '11111111-1111-1111-1111-111111111111',
    institutionCode: 'HSBC-01',
    currency: 'EUR',
    noticePeriod: '24H',
    rate: 3.25,
    valueDate: '2026-05-31',
    endDate: OPEN_END_SENTINEL,
    status: 'PENDING_CONFIRMATION',
  };

  const validSegment: OnCallRateSegment = {
    segmentId: '22222222-2222-2222-2222-222222222222',
    institutionCode: 'HSBC-01',
    currency: 'USD',
    noticePeriod: '48H',
    rate: 4.1,
    valueDate: '2026-04-01',
    endDate: OPEN_END_SENTINEL,
    status: 'VALID',
  };

  const canceledSegment: OnCallRateSegment = {
    segmentId: '33333333-3333-3333-3333-333333333333',
    institutionCode: 'HSBC-01',
    currency: 'EUR',
    noticePeriod: '48H',
    rate: 2,
    valueDate: '2026-01-01',
    endDate: OPEN_END_SENTINEL,
    status: 'CANCELED',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OnCallRateSettingsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: TraderContextService, useValue: { traderId: () => 'trader-test' } },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OnCallRateSettingsComponent);
    fixture.detectChanges();
    flushInstitutions([HSBC]);
    flushSegments([]);
  });

  afterEach(() => {
    http.verify();
  });

  function flushInstitutions(list: Institution[]): void {
    http.match((r) => r.url === '/api/v1/settings/institutions').forEach((r) => r.flush(list));
  }

  function flushSegments(list: OnCallRateSegment[]): void {
    http
      .match((r) => r.url.includes('/oncall-rates') && r.method === 'GET')
      .forEach((r) => r.flush(list));
  }

  function reviewTree(): HTMLElement {
    return fixture.nativeElement.querySelector('[data-testid="oncall-rates-review-tree"]');
  }

  function loadSegmentsForTree(segments: OnCallRateSegment[]): void {
    fixture.componentInstance.segments.set(segments);
    fixture.componentInstance.loading.set(false);
    fixture.detectChanges();
  }

  it('renders curve-point review tree instead of flat mmx-table', () => {
    loadSegmentsForTree([pendingSegment, validSegment]);
    expect(reviewTree()).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.settings-table-wrap table.mmx-table')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.settings-review-tree__curve-point').length).toBe(2);
  });

  it('omits canceled segments from the review tree', () => {
    loadSegmentsForTree([pendingSegment, canceledSegment]);
    const treeText = reviewTree().textContent ?? '';
    expect(treeText).toContain('EUR · 24H');
    expect(treeText).not.toContain('EUR · 48H');
  });

  it('shows current rate summary on collapsed curve-point headers', () => {
    loadSegmentsForTree([pendingSegment, validSegment]);
    const summaries = [
      ...fixture.nativeElement.querySelectorAll('.settings-review-tree__curve-point > summary'),
    ];
    expect(summaries[0].textContent).toContain('3.2500');
    expect(summaries[0].textContent).toContain('Pending confirmation');
    expect(summaries[1].textContent).toContain('4.1000');
    expect(summaries[1].textContent).toContain('Valid');
  });

  it('starts with review tree collapsed on load', () => {
    loadSegmentsForTree([pendingSegment]);
    const details = reviewTree().querySelector(
      '.settings-review-tree__curve-point'
    ) as HTMLDetailsElement;
    expect(details.open).toBe(false);
  });

  it('expand all opens every curve point; collapse all closes them', () => {
    loadSegmentsForTree([pendingSegment, validSegment]);
    const buttons = [
      ...fixture.nativeElement.querySelectorAll('.settings-review-tree__toolbar button'),
    ] as HTMLButtonElement[];
    buttons[0].click();
    fixture.detectChanges();

    const openNodes = reviewTree().querySelectorAll(
      '.settings-review-tree__curve-point'
    ) as NodeListOf<HTMLDetailsElement>;
    expect([...openNodes].every((n) => n.open)).toBe(true);
    expect(reviewTree().textContent).toContain('2026-05-31');

    buttons[1].click();
    fixture.detectChanges();
    expect([...openNodes].every((n) => n.open)).toBe(false);
  });

  it('changing institution resets review tree to collapsed', () => {
    loadSegmentsForTree([pendingSegment]);
    const expandBtn = fixture.nativeElement.querySelector(
      '.settings-review-tree__toolbar button'
    ) as HTMLButtonElement;
    expandBtn.click();
    fixture.detectChanges();

    fixture.componentInstance.onInstitutionChange(CITI.institutionCode);
    const loadReq = http.expectOne('/api/v1/settings/institutions/CITI-01/oncall-rates');
    loadReq.flush([validSegment]);
    fixture.detectChanges();

    const details = reviewTree().querySelector(
      '.settings-review-tree__curve-point'
    ) as HTMLDetailsElement;
    expect(details.open).toBe(false);
    expect(
      fixture.componentInstance.isCurvePointSelected('EUR', '24H')
    ).toBe(false);
  });

  it('expanding a curve point syncs add form and applies selected highlight', () => {
    loadSegmentsForTree([pendingSegment, validSegment]);
    const eurDetails = reviewTree().querySelector(
      '.settings-review-tree__curve-point'
    ) as HTMLDetailsElement;
    eurDetails.open = true;
    eurDetails.dispatchEvent(new Event('toggle'));
    fixture.detectChanges();

    expect(fixture.componentInstance.addForm.currency).toBe('EUR');
    expect(fixture.componentInstance.addForm.noticePeriod).toBe('24H');
    expect(eurDetails.classList.contains('settings-review-tree__curve-point--selected')).toBe(true);
  });

  it('displays sentinel end date as Open in segment rows', () => {
    loadSegmentsForTree([pendingSegment]);
    const expandBtn = fixture.nativeElement.querySelector(
      '.settings-review-tree__toolbar button'
    ) as HTMLButtonElement;
    expandBtn.click();
    fixture.detectChanges();

    const rowText = reviewTree().querySelector('.settings-review-tree__segment-table tbody')?.textContent ?? '';
    expect(rowText).toContain('Open');
    expect(rowText).not.toContain(OPEN_END_SENTINEL);
  });

  it('renders pending confirmation status badge in expanded segment row', () => {
    loadSegmentsForTree([pendingSegment]);
    const expandBtn = fixture.nativeElement.querySelector(
      '.settings-review-tree__toolbar button'
    ) as HTMLButtonElement;
    expandBtn.click();
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Pending confirmation');
    expect(el.querySelector('.status-badge--pending')).toBeTruthy();
  });

  it('adds a rate via API', () => {
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    comp.addForm = {
      currency: 'EUR',
      noticePeriod: '24H',
      rate: 3.5,
      valueDate: '2026-06-01',
    };
    comp.submitAdd();

    const req = http.expectOne('/api/v1/settings/institutions/HSBC-01/oncall-rates');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      currency: 'EUR',
      noticePeriod: '24H',
      rate: 3.5,
      valueDate: '2026-06-01',
    });
    req.flush({ ...pendingSegment, rate: 3.5, valueDate: '2026-06-01' });

    const reload = http.expectOne('/api/v1/settings/institutions/HSBC-01/oncall-rates');
    reload.flush([{ ...pendingSegment, rate: 3.5, valueDate: '2026-06-01' }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('awaiting back-office confirmation');
  });

  it('cancels a pending segment', () => {
    loadSegmentsForTree([pendingSegment]);
    const expandBtn = fixture.nativeElement.querySelector(
      '.settings-review-tree__toolbar button'
    ) as HTMLButtonElement;
    expandBtn.click();
    fixture.detectChanges();

    fixture.componentInstance.cancelSegment(pendingSegment);

    const cancelReq = http.expectOne(
      '/api/v1/settings/institutions/HSBC-01/oncall-rates/11111111-1111-1111-1111-111111111111/cancel'
    );
    expect(cancelReq.request.method).toBe('POST');
    cancelReq.flush({ ...pendingSegment, status: 'CANCELED' });

    const reload = http.expectOne('/api/v1/settings/institutions/HSBC-01/oncall-rates');
    reload.flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Pending rate canceled');
  });
});
