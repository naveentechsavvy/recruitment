package com.example.recruitment.dto;

import com.example.recruitment.entity.Client;

/** What the client's edit panel reads. The slug lets the page confirm the login belongs to this website. */
public record SiteSettingsResponse(
        String slug,
        String name,
        String applicationName,
        String description,
        String logoUrl,
        boolean frozen,
        FooterDetails footer
) {
    public static SiteSettingsResponse from(Client c) {
        return new SiteSettingsResponse(c.getSlug(), c.getName(), c.getApplicationName(), c.getDescription(),
                c.getLogoUrl(), c.isFrozen(), FooterDetails.from(c));
    }
}