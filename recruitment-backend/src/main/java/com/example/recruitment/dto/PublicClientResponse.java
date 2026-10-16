package com.example.recruitment.dto;

import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.ClientProcess;

import java.util.Comparator;
import java.util.List;

/** What the PUBLIC application page may see about a client. No ids, status or internal data. */
public record PublicClientResponse(
        String slug,
        String name,
        String applicationName,
        String description,
        String logoUrl,
        String theme,
        boolean frozen,          // a frozen portal is shown, but it takes no new applications
        String headerColor,      // null = use the theme's colour
        String footerColor,
        FooterDetails footer,
        List<Stage> stages
) {
    public record Stage(String code, String name, String description) { }

    public static PublicClientResponse from(Client c) {
        List<Stage> stages = c.getClientProcesses().stream()
                .filter(ClientProcess::isEnabled)
                .sorted(Comparator.comparingInt(ClientProcess::getDisplayOrder))
                .map(cp -> new Stage(cp.getProcess().getCode(), cp.getProcess().getName(), cp.getProcess().getDescription()))
                .toList();
        return new PublicClientResponse(c.getSlug(), c.getName(), c.getApplicationName(),
                c.getDescription(), c.getLogoUrl(), c.getTheme(), c.isFrozen(),
                c.getHeaderColor(), c.getFooterColor(), FooterDetails.from(c), stages);
    }
}
