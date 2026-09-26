import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SettingsShellComponent } from './settings-shell.component';
import { SETTINGS_ROUTES } from './settings.routes';
import { TraderContextService } from '../../core/trader/trader-context.service';

describe('SettingsShellComponent', () => {
  let fixture: ComponentFixture<SettingsShellComponent>;
  let router: Router;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SettingsShellComponent, HttpClientTestingModule],
      providers: [
        provideRouter([
          {
            path: 'settings',
            component: SettingsShellComponent,
            children: SETTINGS_ROUTES,
          },
        ]),
        {
          provide: TraderContextService,
          useValue: {
            userId: signal('demo-trader'),
            traderId: signal('demo-trader'),
            isTrader: () => true,
            isClientRepresentative: () => false,
          },
        },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SettingsShellComponent);
  });

  afterEach(() => {
    httpMock.match(() => true).forEach((r) => {
      try {
        r.flush([]);
      } catch {
        /* already flushed */
      }
    });
    httpMock.verify();
  });

  function flushChildRouteRequests(): void {
    httpMock.match((r) => r.url === '/api/v1/settings/currencies').forEach((r) => r.flush([]));
    httpMock
      .match((r) => r.url === '/api/v1/settings/term-rates/days')
      .forEach((r) => r.flush([]));
    httpMock.match((r) => r.url === '/api/v1/settings/institutions').forEach((r) => r.flush([]));
    httpMock
      .match(
        (r) =>
          r.method === 'GET' &&
          r.url.startsWith('/api/v1/settings/term-rates') &&
          !r.url.includes('/sample') &&
          !r.url.includes('/days') &&
          !r.url.includes('/upload')
      )
      .forEach((r) => r.flush([]));
  }

  it('highlights Currencies sub-nav', async () => {
    await router.navigateByUrl('/settings/currencies');
    fixture.detectChanges();
    flushChildRouteRequests();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toContain('/settings/currencies');
    const links = fixture.nativeElement.querySelectorAll('.settings-hub-nav a') as NodeListOf<HTMLAnchorElement>;
    expect(links[0].getAttribute('href')).toContain('/settings/currencies');
    expect(links[1].getAttribute('href')).toContain('/settings/institutions');
    expect(links[2].getAttribute('href')).toContain('/settings/delegated-grants');
    expect(links[3].getAttribute('href')).toContain('/settings/term-rates');
    expect(links[4].getAttribute('href')).toContain('/settings/oncall-rates');
    expect(router.isActive('/settings/currencies', false)).toBe(true);
    expect(router.isActive('/settings/institutions', false)).toBe(false);
  });

  it('highlights Term rates sub-nav', async () => {
    await router.navigateByUrl('/settings/term-rates');
    fixture.detectChanges();
    flushChildRouteRequests();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toContain('/settings/term-rates');
    expect(router.isActive('/settings/term-rates', false)).toBe(true);
  });
});
