import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { TraderContextService } from './core/trader/trader-context.service';

describe('App', () => {
  beforeEach(async () => {
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

  it('shows Currencies link in header', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('.header-link') as HTMLAnchorElement;
    expect(link?.textContent?.trim()).toBe('Currencies');
    expect(link?.getAttribute('href')).toContain('/settings/currencies');
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
