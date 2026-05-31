import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { OnCallRateSettingsComponent } from './oncall-rate-settings.component';
import { TraderContextService } from '../../core/trader/trader-context.service';
import type { OnCallRateSegment } from '../../core/api/oncall-rate-settings-api.service';
import type { Institution } from '../../core/api/institution-settings-api.service';

describe('OnCallRateSettingsComponent', () => {
  let fixture: ComponentFixture<OnCallRateSettingsComponent>;
  let http: HttpTestingController;

  const HSBC: Institution = { institutionCode: 'HSBC-01', displayName: 'HSBC', active: true };

  const pendingSegment: OnCallRateSegment = {
    segmentId: '11111111-1111-1111-1111-111111111111',
    institutionCode: 'HSBC-01',
    currency: 'EUR',
    noticePeriod: '24H',
    rate: 3.25,
    valueDate: '2026-05-31',
    endDate: '2999-12-31',
    status: 'PENDING_CONFIRMATION',
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

  it('renders pending confirmation status badge', () => {
    fixture.componentInstance.segments.set([pendingSegment]);
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
    flushSegments([pendingSegment]);
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
