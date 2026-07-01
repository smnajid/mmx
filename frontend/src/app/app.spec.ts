import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { TraderContextService } from './core/trader/trader-context.service';

describe('App', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        TraderContextService,
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.match(() => true).forEach((req) => {
      if (!req.cancelled) {
        req.flush({ content: [], totalElements: 0, page: 0, size: 20 });
      }
    });
    TestBed.resetTestingModule();
  });

  function createFixture(): ComponentFixture<App> {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    return fixture;
  }

  it('should create the app', () => {
    const fixture = createFixture();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show MMx brand', async () => {
    const fixture = createFixture();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.name')?.textContent).toContain('MMx');
  });

  it('shows Desk and Settings links in header for Trader', async () => {
    const fixture = createFixture();
    await fixture.whenStable();
    const links = fixture.nativeElement.querySelectorAll('.header-link') as NodeListOf<HTMLAnchorElement>;
    expect(links.length).toBe(2);
    expect(links[0].textContent?.trim()).toBe('Desk');
    expect(links[1].textContent?.trim()).toBe('Settings');
    expect(links[1].getAttribute('href')).toContain('/settings');
  });

  it('hides Desk entry for ClientRepresentative', async () => {
    const user = TestBed.inject(TraderContextService);
    user.bindActiveScope({ legalEntityCode: 'PAR', role: 'CLIENT_REPRESENTATIVE' });
    const fixture = createFixture();
    await fixture.whenStable();
    const links = fixture.nativeElement.querySelectorAll('.header-link');
    expect(links.length).toBe(1);
    expect(links[0].textContent?.trim()).toBe('Settings');
  });

  it('redirects ClientRepresentative from desk route to Settings', async () => {
    const user = TestBed.inject(TraderContextService);
    user.bindActiveScope({ legalEntityCode: 'PAR', role: 'CLIENT_REPRESENTATIVE' });
    const fixture = createFixture();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/term/received');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(router.url).toContain('/settings');
  });

  it('redirects ClientRepresentative from oncall desk route to Settings', async () => {
    const user = TestBed.inject(TraderContextService);
    user.bindActiveScope({ legalEntityCode: 'PAR', role: 'CLIENT_REPRESENTATIVE' });
    const fixture = createFixture();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/oncall/assigned');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(router.url).toContain('/settings');
  });

  it('shows Desk link on settings route', async () => {
    const fixture = createFixture();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/settings/currencies');
    fixture.detectChanges();
    await fixture.whenStable();
    const deskLink = fixture.nativeElement.querySelector('.header-link') as HTMLAnchorElement;
    expect(deskLink?.textContent?.trim()).toBe('Desk');
  });

  it('marks Settings active on settings route', async () => {
    const fixture = createFixture();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/settings/currencies');
    fixture.detectChanges();
    const links = fixture.nativeElement.querySelectorAll('.header-link');
    expect(links[0].classList.contains('active')).toBe(false);
    expect(links[1].classList.contains('active')).toBe(true);
  });

  it('shows settings sub-nav on institutions route', async () => {
    const fixture = createFixture();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/settings/institutions');
    fixture.detectChanges();
    await fixture.whenStable();
    const hub = fixture.nativeElement.querySelector('.settings-hub-nav');
    expect(hub).toBeTruthy();
    const tabs = hub?.querySelectorAll('a') ?? [];
    expect(tabs.length).toBe(5);
    expect(tabs[1].classList.contains('active')).toBe(true);
  });

  it('marks Desk active on desk queue route', async () => {
    const fixture = createFixture();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/term/received');
    fixture.detectChanges();
    const links = fixture.nativeElement.querySelectorAll('.header-link');
    expect(links[0].classList.contains('active')).toBe(true);
    expect(links[1].classList.contains('active')).toBe(false);
  });

  it('Desk link uses remembered return URL after desk then settings', async () => {
    const fixture = createFixture();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/term/assigned');
    await fixture.whenStable();
    await router.navigateByUrl('/settings/currencies');
    fixture.detectChanges();
    const deskLink = fixture.nativeElement.querySelector('.header-link') as HTMLAnchorElement;
    expect(deskLink.getAttribute('href')).toContain('/term/assigned');
  });

  it('Desk link falls back to oncall received when no prior desk visit', async () => {
    const fixture = createFixture();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/settings/currencies');
    fixture.detectChanges();
    const deskLink = fixture.nativeElement.querySelector('.header-link') as HTMLAnchorElement;
    expect(deskLink.getAttribute('href')).toContain('/oncall/received');
  });

  it('hides desk nav on settings route', async () => {
    const fixture = createFixture();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/settings/currencies');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.desk-nav')).toBeNull();
  });

  it('shows ON-CALL and Term primary tabs in main content', async () => {
    const fixture = createFixture();
    await fixture.whenStable();
    fixture.detectChanges();

    const main = fixture.nativeElement.querySelector('main') as HTMLElement;
    const primary = main.querySelectorAll('.desk-primary-tabs a');
    expect(primary.length).toBe(2);
    expect(primary[0].textContent?.trim()).toBe('ON-CALL');
    expect(primary[1].textContent?.trim()).toBe('Term');
    expect(fixture.nativeElement.querySelector('.workspace-nav')).toBeNull();
  });

  describe('scope switcher', () => {
    it('re-scopes to a held scope and navigates to Settings for ClientRepresentative', async () => {
      const user = TestBed.inject(TraderContextService);
      const fixture = createFixture();
      const router = TestBed.inject(Router);
      await router.navigateByUrl('/term/received');
      fixture.detectChanges();

      const select = fixture.nativeElement.querySelector(
        '[data-testid="scope-switcher"]',
      ) as HTMLSelectElement;
      select.value = 'PAR:CLIENT_REPRESENTATIVE';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      const req = httpMock.expectOne('/api/v1/session/scope');
      expect(req.request.headers.get('X-User-Id')).toBe('demo-trader');
      expect(req.request.body).toEqual({
        legalEntityCode: 'PAR',
        role: 'CLIENT_REPRESENTATIVE',
      });
      req.flush({ legalEntityCode: 'PAR', role: 'CLIENT_REPRESENTATIVE' });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(user.activeScope()).toEqual({
        legalEntityCode: 'PAR',
        role: 'CLIENT_REPRESENTATIVE',
      });
      expect(router.url).toContain('/settings');
    });

    it('re-scopes to another held Trader scope without leaving desk', async () => {
      const user = TestBed.inject(TraderContextService);
      user.bindActiveScope({ legalEntityCode: 'PAR', role: 'CLIENT_REPRESENTATIVE' });
      const fixture = createFixture();
      const router = TestBed.inject(Router);
      await router.navigateByUrl('/settings/currencies');
      fixture.detectChanges();

      const select = fixture.nativeElement.querySelector(
        '[data-testid="scope-switcher"]',
      ) as HTMLSelectElement;
      select.value = 'LOC:TRADER';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      const req = httpMock.expectOne('/api/v1/session/scope');
      req.flush({ legalEntityCode: 'LOC', role: 'TRADER' });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(user.activeScope()).toEqual({ legalEntityCode: 'LOC', role: 'TRADER' });
      expect(router.url).toContain('/settings');
    });

    it('keeps active scope when re-scope to unheld scope is rejected', async () => {
      const user = TestBed.inject(TraderContextService);
      const fixture = createFixture();
      const previous = user.activeScope();

      const select = fixture.nativeElement.querySelector(
        '[data-testid="scope-switcher"]',
      ) as HTMLSelectElement;
      select.value = 'PAR:CLIENT_REPRESENTATIVE';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      const req = httpMock.expectOne('/api/v1/session/scope');
      req.flush(
        { error: 'FORBIDDEN', message: 'Scope not held' },
        { status: 403, statusText: 'Forbidden' },
      );
      await fixture.whenStable();
      fixture.detectChanges();

      expect(user.activeScope()).toEqual(previous);
      expect(select.value).toBe(user.scopeKey(previous));
    });
  });

  describe('desk navigation highlighting', () => {
    let fixture: ComponentFixture<App>;
    let router: Router;

    beforeEach(async () => {
      fixture = createFixture();
      router = TestBed.inject(Router);
      fixture.detectChanges();
    });

    it('marks Term and Assigned active from /term/assigned', async () => {
      await router.navigateByUrl('/term/assigned');
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      const primary = el.querySelectorAll('.desk-primary-tabs a');
      expect(primary[0].classList.contains('active')).toBe(false);
      expect(primary[1].classList.contains('active')).toBe(true);

      const sub = el.querySelectorAll('.desk-sub-tabs a');
      expect(sub[0].classList.contains('active')).toBe(false);
      expect(sub[1].classList.contains('active')).toBe(true);
      expect(sub[2].classList.contains('active')).toBe(false);
    });

    it('marks ON-CALL and Executed active from order-details URL query params', async () => {
      await router.navigateByUrl(
        '/orders/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee?ws=oncall&queue=executed',
      );
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      const primary = el.querySelectorAll('.desk-primary-tabs a');
      expect(primary[0].classList.contains('active')).toBe(true);
      expect(primary[1].classList.contains('active')).toBe(false);

      const sub = el.querySelectorAll('.desk-sub-tabs a');
      expect(sub[2].classList.contains('active')).toBe(true);
    });

    it('does not mark primary tabs active on order details without ws/queue', async () => {
      await router.navigateByUrl('/orders/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee');
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      const primary = el.querySelectorAll('.desk-primary-tabs a');
      expect(primary[0].classList.contains('active')).toBe(false);
      expect(primary[1].classList.contains('active')).toBe(false);
    });
  });
});
