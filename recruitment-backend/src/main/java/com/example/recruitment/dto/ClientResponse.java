package com.example.recruitment.dto;

import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.ClientProcess;
import com.example.recruitment.entity.ClientStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** What the API returns for a client. Never expose entities directly. */
public record ClientResponse(
        Long id,
        String name,
        String slug,
        String description,
        String logoUrl,
        String applicationName,
        String theme,
        ClientStatus status,
        List<String> processCodes,   // enabled processes, in display order
        boolean applicationGenerated, // true once the application is out of Draft: the screen then shows "Save", not "Generate Application"
        boolean frozen,
        Instant frozenAt,
        String frozenBy,
        String headerColor,          // null = use the theme's colour
        String footerColor,
        FooterDetails footer,
        Instant createdAt,
        Instant updatedAt
) {
    public static ClientResponse from(Client c) {
        List<String> codes = c.getClientProcesses().stream()
                .filter(ClientProcess::isEnabled)
                .sorted(Comparator.comparingInt(ClientProcess::getDisplayOrder))
                .map(cp -> cp.getProcess().getCode())
                .toList();
        return new ClientResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getLogoUrl(),
                c.getApplicationName(), c.getTheme(), c.getStatus(), codes,
                c.getStatus() != ClientStatus.DRAFT, c.isFrozen(), c.getFrozenAt(), c.getFrozenBy(),
                c.getHeaderColor(), c.getFooterColor(), FooterDetails.from(c),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
