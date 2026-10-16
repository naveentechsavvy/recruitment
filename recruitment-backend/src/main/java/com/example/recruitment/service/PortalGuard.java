package com.example.recruitment.service;

import com.example.recruitment.entity.Client;
import com.example.recruitment.exception.PortalFrozenException;

/**
 * THE rule for a frozen portal. While frozen, nothing new can be added or changed inside it:
 * no applications, candidates, screening, stage decisions, remarks, resumes, jobs or hiring managers.
 * Everything is kept and still readable. An admin can still edit the portal itself, manage its roles,
 * unfreeze it or delete it.
 */
public final class PortalGuard {

    public static final String MESSAGE =
            "This client portal is frozen. This action is not available until the portal is unfrozen.";

    private PortalGuard() { }

    public static void requireActive(Client client) {
        if (client != null && client.isFrozen()) throw new PortalFrozenException(MESSAGE);
    }
}
