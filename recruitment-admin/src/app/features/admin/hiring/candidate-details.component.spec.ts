import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Candidate } from '../../../core/models/candidate.model';
import { CandidateDetailsComponent } from './candidate-details.component';

const BASE = 'http://localhost:8080/api/candidates/5';

function candidate(over: Partial<Candidate> = {}): Candidate {
  return {
    id: 5, clientId: 1, requirementId: 10, jobId: 'J-1', jobTitle: 'Flutter Developer',
    fullName: 'Asha Rao', email: 'asha@example.com', phone: null,
    status: 'ACTIVE', screeningStatus: 'PENDING', currentStageCode: 'resume-screening', currentStageName: 'Resume Screening',
    hasResume: true, resumeName: 'asha.pdf', appliedAt: '2026-10-01T10:00:00Z',
    totalExperience: null, relevantExperience: null, currentCtc: null, expectedCtc: null, noticePeriodDays: null, skills: null,
    missingScreeningFields: [], canScreen: true, canRemark: true, stages: [], timeline: [],
    ...over,
  };
}

describe('CandidateDetailsComponent screening', () => {
  let http: HttpTestingController;
  let cmp: CandidateDetailsComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ clientId: '1', candidateId: '5' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    cmp = TestBed.createComponent(CandidateDetailsComponent).componentInstance;
    http.expectOne(BASE).flush(candidate());
  });

  afterEach(() => http.verify());

  const fillDetails = () => {
    cmp.screening = { totalExperience: 5, relevantExperience: 3, currentCtc: 800000, expectedCtc: 1200000, noticePeriodDays: 30, skills: ' Flutter, Dart ', remarks: '' };
  };

  it('blocks Reject without a reason and sends nothing', () => {
    cmp.openDialog('REJECT');
    cmp.dialogRemarks = '   ';
    cmp.confirmDialog();

    expect(cmp.dialogError).toContain('reason');
    http.expectNone(`${BASE}/screening/action`);
  });

  it('sends Reject with the trimmed reason and closes the dialog', () => {
    cmp.openDialog('REJECT');
    cmp.dialogRemarks = '  Skills do not match ';
    cmp.confirmDialog();

    const req = http.expectOne(`${BASE}/screening/action`);
    expect(req.request.body).toEqual({ action: 'REJECT', remarks: 'Skills do not match' });
    req.flush(candidate({ status: 'REJECTED', screeningStatus: 'REJECTED', canScreen: false }));

    expect(cmp.dialog).toBeNull();
    expect(cmp.notice).toContain('rejected');
  });

  it('blocks Proceed while required details are missing', () => {
    cmp.openDialog('PROCEED');

    expect(cmp.dialog).toBeNull();
    expect(cmp.errs.totalExperience).toBeTruthy();
    expect(cmp.errs.skills).toBeTruthy();
    http.expectNone(`${BASE}/screening`);
  });

  it('rejects negative CTC and a notice period over 365 days', () => {
    cmp.screening.currentCtc = -5;
    cmp.screening.noticePeriodDays = 400;
    expect(cmp.revalidate(false)).toBeFalse();
    expect(cmp.errs.currentCtc).toContain('negative');
    expect(cmp.errs.noticePeriodDays).toContain('365');
  });

  it('blocks Proceed when there is no resume', () => {
    cmp.candidate = candidate({ hasResume: false });
    fillDetails();
    cmp.openDialog('PROCEED');

    expect(cmp.dialog).toBeNull();
    expect(cmp.error).toContain('resume');
  });

  it('saves the trimmed details, then records Proceed, and ignores a double click', () => {
    fillDetails();
    cmp.openDialog('PROCEED');
    expect(cmp.dialog).toBe('PROCEED');

    cmp.confirmDialog();
    cmp.confirmDialog(); // second click while busy must not send anything

    const save = http.expectOne(`${BASE}/screening`);
    expect(save.request.method).toBe('PUT');
    expect(save.request.body.skills).toBe('Flutter, Dart');
    save.flush(candidate());

    const action = http.expectOne(`${BASE}/screening/action`);
    expect(action.request.body.action).toBe('PROCEED');
    action.flush(candidate({ screeningStatus: 'PROCEEDED', currentStageCode: 'hiring-manager-review', canScreen: false }));

    expect(cmp.candidate?.screeningStatus).toBe('PROCEEDED');
    expect(cmp.busy).toBeFalse();
  });

  it('shows the server message when a decision is refused', () => {
    cmp.openDialog('ON_HOLD');
    cmp.dialogRemarks = 'Waiting for budget';
    cmp.confirmDialog();

    http.expectOne(`${BASE}/screening`).flush(candidate());
    http.expectOne(`${BASE}/screening/action`).flush({ message: 'This candidate is already on hold' }, { status: 400, statusText: 'Bad Request' });
    http.expectOne(BASE).flush(candidate()); // the page reloads after a failed decision

    expect(cmp.dialogError).toContain('already on hold');
  });
});
