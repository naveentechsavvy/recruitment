import { Component, inject, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { SiteCandidateService, OnboardingCandidateView } from '../../../core/services/site-candidate.service';
import { siteSlug } from '../../../core/services/site-hiring.service';

@Component({
  selector: 'app-site-onboarding',
  standalone: true,
  imports: [DatePipe, RouterLink],
  styles: `
    .page-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
      flex-wrap: wrap;
      gap: 12px;
    }

    h1 { margin: 0; font-size: 26px; }
    .muted { color: #64748b; font-size: 14px; }

    .table-wrap {
      overflow-x: auto;
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
    }

    table {
      width: 100%;
      border-collapse: collapse;
      min-width: 650px;
    }

    th, td {
      padding: 14px 16px;
      text-align: left;
      border-bottom: 1px solid #e2e8f0;
      font-size: 14px;
    }

    th {
      background: #f8fafc;
      color: #475569;
      font-weight: 600;
    }

    a {
      color: #2563eb;
      text-decoration: none;
      font-weight: 600;
    }

    a:hover { text-decoration: underline; }

    .status {
      display: inline-block;
      padding: 5px 10px;
      border-radius: 20px;
      background: #dcfce7;
      color: #166534;
      font-size: 12px;
      font-weight: 600;
    }

    .message {
      padding: 24px;
      text-align: center;
      color: #64748b;
    }
  `,
  template: `
    <div class="page-head">
      <div>
        <h1>Onboarding</h1>
        <p class="muted">Candidates who have completed pre-onboarding approval.</p>
      </div>
    </div>

    @if (loading) {
      <div class="message">Loading candidates...</div>
    } @else if (error) {
      <div class="message">{{ error }}</div>
    } @else if (candidates.length === 0) {
      <div class="message">No completed onboarding candidates found.</div>
    } @else {
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Candidate</th>
              <th>Email</th>
              <th>Phone</th>
              <th>Requirement ID</th>
              <th>Status</th>
              <th>Case Created</th>
            </tr>
          </thead>
          <tbody>
            @for (candidate of candidates; track candidate.candidateId) {
              <tr>
                <td>
                  <a [routerLink]="[
                    '/app', slug, 'manage', 'candidates', candidate.candidateId
                  ]">
                    {{ candidate.fullName }}
                  </a>
                </td>
                <td>{{ candidate.email }}</td>
                <td>{{ candidate.phone || '-' }}</td>
                <td>{{ candidate.requirementId }}</td>
                <td><span class="status">Completed</span></td>
                <td>{{ candidate.caseCreatedAt | date:'dd MMM yyyy' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class SiteOnboardingComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private candidateService = inject(SiteCandidateService);

  slug = siteSlug(this.route);
  candidates: OnboardingCandidateView[] = [];
  loading = true;
  error = '';

  ngOnInit(): void {
    this.candidateService.forSite(this.slug).getOnboardingCandidates().subscribe({
      next: (result) => {
        this.candidates = result;
        this.loading = false;
      },
      error: () => {
        this.error = 'Unable to load onboarding candidates. Please try again.';
        this.loading = false;
      },
    });
  }
}
