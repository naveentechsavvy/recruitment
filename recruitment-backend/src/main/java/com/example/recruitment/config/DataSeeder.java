package com.example.recruitment.config;

import com.example.recruitment.entity.RecruitmentProcess;
import com.example.recruitment.repository.RecruitmentProcessRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * At every start, makes sure the 18 standard recruitment stages exist with the right name and description.
 * Missing stages are added. Existing stages keep their id (so no client or candidate loses a link),
 * only their name and description are brought up to date. Stages not in this list
 * (the older "technical-round" and "rejected") are left exactly as they are.
 *
 * The classification (Standard / Recommended / Optional) and the display order are admin-form settings
 * and live in the Angular stage list. The order a client uses is saved per client in client_processes.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    /** code, name, description. Same order as the form. */
    private static final List<String[]> STAGES = List.of(
            new String[]{"candidate-application", "Candidate Application", "Candidate submits an application for a job opening."},
            new String[]{"resume-screening", "Resume Screening", "Recruiter reviews resumes and checks eligibility against job requirements."},
            new String[]{"hiring-manager-review", "Hiring Manager Review", "Hiring manager reviews shortlisted candidate profiles."},
            new String[]{"interview-scheduling", "Interview Scheduling", "Recruiter schedules interviews and sends invitations to candidates and interviewers."},
            new String[]{"technical-round-l1", "Technical Round L1", "First-level technical interview or assessment."},
            new String[]{"technical-round-l2", "Technical Round L2", "Second-level technical interview or assessment."},
            new String[]{"technical-functional-assessment", "Technical / Functional Assessment", "Candidate completes a coding test, assignment, skill test, or functional assessment."},
            new String[]{"interview-feedback", "Interview Feedback", "Interviewers submit ratings, comments, and recommendations."},
            new String[]{"manager-round", "Manager Round", "Hiring manager or project manager evaluates experience, delivery skills, and team fit."},
            new String[]{"hr-round", "HR Round", "HR evaluates communication, culture fit, availability, and employment details."},
            new String[]{"compensation-discussion", "Compensation Discussion", "Recruiter discusses salary expectations, budget, notice period, and benefits."},
            new String[]{"final-selection", "Final Selection", "Hiring team confirms the final hiring decision."},
            new String[]{"offer-approval", "Offer Approval", "Authorized stakeholders approve compensation and employment terms."},
            new String[]{"offer-released", "Offer Released", "Official offer letter is issued to the candidate."},
            new String[]{"offer-accepted", "Offer Accepted", "Candidate formally accepts the offer."},
            new String[]{"background-verification", "Background Verification", "Employment, education, identity, and reference checks are performed as applicable."},
            new String[]{"pre-boarding", "Pre-boarding", "Candidate completes pre-joining documentation and joining formalities."},
            new String[]{"onboarding-joined", "Onboarding / Joined", "Candidate joins the organization and completes onboarding."}
    );

    private final RecruitmentProcessRepository repository;

    @PersistenceContext
    private EntityManager em;

    public DataSeeder(RecruitmentProcessRepository repository) { this.repository = repository; }

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, RecruitmentProcess> existing = repository.findAll().stream()
                .collect(Collectors.toMap(RecruitmentProcess::getCode, Function.identity()));

        List<RecruitmentProcess> toAdd = new ArrayList<>();
        for (String[] s : STAGES) {
            RecruitmentProcess current = existing.get(s[0]);
            if (current == null) {
                toAdd.add(new RecruitmentProcess(s[0], s[1], s[2]));
            } else if (!Objects.equals(current.getName(), s[1]) || !Objects.equals(current.getDescription(), s[2])) {
                em.createQuery("update RecruitmentProcess p set p.name = :name, p.description = :description where p.code = :code")
                        .setParameter("name", s[1])
                        .setParameter("description", s[2])
                        .setParameter("code", s[0])
                        .executeUpdate();
            }
        }
        if (!toAdd.isEmpty()) repository.saveAll(toAdd);
    }
}
