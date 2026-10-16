import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { PublicApp } from '../../core/models/public-app.model';
import { ClientLoginComponent } from './client-login.component';

const PUBLIC = 'http://localhost:8080/api/public/clients';
const LOGIN = 'http://localhost:8080/api/auth/login';
const SESSION_KEY = 'velzonix.admin.session';

const app = (over: Partial<PublicApp> = {}): PublicApp => ({
  slug: 'apple', name: 'Apple', applicationName: 'Apple Careers', description: null, logoUrl: '/uploads/apple.png',
  theme: 'default', headerColor: '#1e40af', footerColor: null, footer: null, stages: [], ...over,
});

/** A token that has not expired, so the app's own checks treat the session as valid. */
const token = () => `x.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.y`;

describe('ClientLoginComponent', () => {
  let http: HttpTestingController;
  let router: Router;

  function create(slug = 'apple') {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ slug })) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    return TestBed.createComponent(ClientLoginComponent).componentInstance;
  }

  function loaded(slug = 'apple', over: Partial<PublicApp> = {}) {
    const cmp = create(slug);
    http.expectOne(`${PUBLIC}/${slug}`).flush(app(over));
    return cmp;
  }

  const fill = (cmp: ClientLoginComponent, email: string, password: string) => cmp.form.setValue({ email, password });

  afterEach(() => { http.verify(); localStorage.removeItem(SESSION_KEY); });

  it('loads the branding of the client named in the address', () => {
    const cmp = loaded('apple');
    expect(cmp.app?.name).toBe('Apple');
    expect(cmp.colors.header).toBe('#1e40af');
    expect(cmp.logo).toBe('http://localhost:8080/uploads/apple.png');
  });

  it('loads another client from its own address, nothing is hardcoded', () => {
    const cmp = loaded('samsung', { slug: 'samsung', name: 'Samsung', applicationName: null, logoUrl: null, headerColor: null, theme: 'teal' });
    expect(cmp.app?.name).toBe('Samsung');
    expect(cmp.logo).toBeNull();
    expect(cmp.colors.header).toBe('#0f766e'); // the teal theme, because no header colour was saved
  });

  it('shows "not found" for an unknown or draft application', () => {
    const cmp = create('nobody');
    http.expectOne(`${PUBLIC}/nobody`).flush({ message: 'Application not found' }, { status: 404, statusText: 'Not Found' });
    expect(cmp.notFound).toBeTrue();
    expect(cmp.app).toBeUndefined();
  });

  it('shows a retry state when the server cannot be reached', () => {
    const cmp = create();
    http.expectOne(`${PUBLIC}/apple`).error(new ProgressEvent('error'), { status: 0 });
    expect(cmp.loadError).toBeTrue();
    expect(cmp.notFound).toBeFalse();
  });

  it('sends nothing and shows messages when the fields are empty', () => {
    const cmp = loaded();
    cmp.submit();
    expect(cmp.showError('email')).toBeTrue();
    expect(cmp.emailMessage).toContain('enter your email');
    expect(cmp.showError('password')).toBeTrue();
    http.expectNone(LOGIN);
  });

  it('rejects an email that is not an email', () => {
    const cmp = loaded();
    fill(cmp, 'not-an-email', 'secret');
    cmp.submit();
    expect(cmp.emailMessage).toContain('valid email');
    http.expectNone(LOGIN);
  });

  it('shows and hides the password', () => {
    const cmp = loaded();
    expect(cmp.show).toBeFalse();
    cmp.show = !cmp.show;
    expect(cmp.show).toBeTrue();
  });

  it('shows a friendly message for a wrong password and stays on the page', () => {
    const cmp = loaded();
    fill(cmp, 'a@apple.com', 'wrong');
    cmp.submit();
    http.expectOne(LOGIN).flush({ message: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });

    expect(cmp.error).toBe('Invalid email or password.');
    expect(cmp.submitting).toBeFalse();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('says so when the server cannot be reached while signing in', () => {
    const cmp = loaded();
    fill(cmp, 'a@apple.com', 'secret');
    cmp.submit();
    http.expectOne(LOGIN).error(new ProgressEvent('error'), { status: 0 });
    expect(cmp.error).toContain('Cannot reach the server');
  });

  it('ignores a second click while signing in', () => {
    const cmp = loaded();
    fill(cmp, 'a@apple.com', 'secret');
    cmp.submit();
    cmp.submit();
    http.expectOne(LOGIN).flush({ token: token(), email: 'a@apple.com', role: 'ADMIN', clientId: null });
    expect(cmp.submitting).toBeFalse();
  });

  it('trims the email before sending it', () => {
    const cmp = loaded();
    fill(cmp, '  a@apple.com ', 'secret');
    cmp.submit();
    const req = http.expectOne(LOGIN);
    expect(req.request.body).toEqual({ email: 'a@apple.com', password: 'secret' });
    req.flush({ token: token(), email: 'a@apple.com', role: 'ADMIN', clientId: null });
  });

  it('lets an admin in on any portal and goes to the dashboard', () => {
    const cmp = loaded();
    fill(cmp, 'admin@velzonix.com', 'secret');
    cmp.submit();
    http.expectOne(LOGIN).flush({ token: token(), email: 'admin@velzonix.com', role: 'ADMIN', clientId: null });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/dashboard');
    expect(localStorage.getItem(SESSION_KEY)).toContain('ADMIN');
  });

  it('lets a person of this company in', () => {
    const cmp = loaded('apple');
    fill(cmp, 'hm@apple.com', 'secret');
    cmp.submit();
    http.expectOne(LOGIN).flush({ token: token(), email: 'hm@apple.com', role: 'HIRING_MANAGER', clientId: 4 });
    http.expectOne('http://localhost:8080/api/clients/4').flush({ slug: 'apple' });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/dashboard');
    expect(cmp.error).toBe('');
  });

  it('turns away a person of another company and leaves no session behind', () => {
    const cmp = loaded('apple');
    fill(cmp, 'hm@samsung.com', 'secret');
    cmp.submit();
    http.expectOne(LOGIN).flush({ token: token(), email: 'hm@samsung.com', role: 'HIRING_MANAGER', clientId: 9 });
    http.expectOne('http://localhost:8080/api/clients/9').flush({ slug: 'samsung' });

    expect(cmp.error).toContain('does not belong to Apple');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(localStorage.getItem(SESSION_KEY)).toBeNull();
    expect(cmp.submitting).toBeFalse();
  });

  it('does not let anyone in when the portal check fails', () => {
    const cmp = loaded('apple');
    fill(cmp, 'hm@apple.com', 'secret');
    cmp.submit();
    http.expectOne(LOGIN).flush({ token: token(), email: 'hm@apple.com', role: 'HIRING_MANAGER', clientId: 4 });
    http.expectOne('http://localhost:8080/api/clients/4').flush({}, { status: 500, statusText: 'Server Error' });

    expect(cmp.error).toContain('could not confirm');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(localStorage.getItem(SESSION_KEY)).toBeNull();
  });
});
