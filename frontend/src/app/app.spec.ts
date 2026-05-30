import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { TraderContextService } from './core/trader/trader-context.service';

describe('App', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        {
          provide: TraderContextService,
          useValue: { traderId: signal('test-trader'), setTraderId: (): void => {} },
        },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should show MMx brand', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.name')?.textContent).toContain('MMx');
  });

  it('shows Desk and Settings links in header', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();
    const links = fixture.nativeElement.querySelectorAll('.header-link') as NodeListOf<HTMLAnchorElement>;
    expect(links.length).toBe(2);
    expect(links[0].textContent?.trim()).toBe('Desk');
    expect(links[1].textContent?.trim()).toBe('Settings');
    expect(links[1].getAttribute('href')).toContain('/settings');
  });

  it('shows Desk link on settings route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/settings/currencies');
    fixture.detectChanges();
    await fixture.whenStable();
    const deskLink = fixture.nativeElement.querySelector('.header-link') as HTMLAnchorElement;
    expect(deskLink?.textContent?.trim()).toBe('Desk');
  });

  it('marks Settings active on settings route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/settings/currencies');
    fixture.detectChanges();
    const links = fixture.nativeElement.querySelectorAll('.header-link');
    expect(links[0].classList.contains('active')).toBe(false);
    expect(links[1].classList.contains('active')).toBe(true);
  });

  it('shows settings sub-nav on institutions route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/settings/institutions');
    fixture.detectChanges();
    await fixture.whenStable();
    const hub = fixture.nativeElement.querySelector('.settings-hub-nav');
    expect(hub).toBeTruthy();
    const tabs = hub?.querySelectorAll('a') ?? [];
    expect(tabs.length).toBe(2);
    expect(tabs[1].classList.contains('active')).toBe(true);
  });

  it('marks Desk active on desk queue route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/term/received');
    fixture.detectChanges();
    const links = fixture.nativeElement.querySelectorAll('.header-link');
    expect(links[0].classList.contains('active')).toBe(true);
    expect(links[1].classList.contains('active')).toBe(false);
  });

  it('Desk link uses remembered return URL after desk then settings', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/term/assigned');
    await fixture.whenStable();
    await router.navigateByUrl('/settings/currencies');
    fixture.detectChanges();
    const deskLink = fixture.nativeElement.querySelector('.header-link') as HTMLAnchorElement;
    expect(deskLink.getAttribute('href')).toContain('/term/assigned');
  });

  it('Desk link falls back to oncall received when no prior desk visit', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/settings/currencies');
    fixture.detectChanges();
    const deskLink = fixture.nativeElement.querySelector('.header-link') as HTMLAnchorElement;
    expect(deskLink.getAttribute('href')).toContain('/oncall/received');
  });

  it('hides desk nav on settings route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/settings/currencies');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.desk-nav')).toBeNull();
  });

  it('shows ON-CALL and Term primary tabs in main content', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();

    const main = fixture.nativeElement.querySelector('main') as HTMLElement;
    const primary = main.querySelectorAll('.desk-primary-tabs a');
    expect(primary.length).toBe(2);
    expect(primary[0].textContent?.trim()).toBe('ON-CALL');
    expect(primary[1].textContent?.trim()).toBe('Term');
    expect(fixture.nativeElement.querySelector('.workspace-nav')).toBeNull();
  });

  describe('desk navigation highlighting', () => {
    let fixture: ComponentFixture<App>;
    let router: Router;

    beforeEach(async () => {
      fixture = TestBed.createComponent(App);
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
        '/orders/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee?ws=oncall&queue=executed'
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
