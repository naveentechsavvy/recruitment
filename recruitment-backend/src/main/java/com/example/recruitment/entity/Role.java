package com.example.recruitment.entity;

/**
 * ADMIN = Velzonix Nova admin (every portal).
 * RECRUITER = recruiting/sourcing team. HIRING_MANAGER = owns jobs of one client.
 * MANAGER and HR_BP (HR business partner) belong to one client portal and see all of its candidates.
 * The roles of a portal are assigned by email in Role Management (see ClientRoleAssignment).
 */
public enum Role {
    ADMIN,
    RECRUITER,
    HIRING_MANAGER,
    MANAGER,
    HR_BP,
    /** The client's own website editor. Can only edit the page content of their own portal (see SiteController). */
    CLIENT_ADMIN
}