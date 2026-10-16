package com.example.recruitment.security;

import com.example.recruitment.entity.Role;
import org.springframework.security.access.AccessDeniedException;

import java.util.Objects;

/**
 * A hiring manager belongs to one client and may only touch that client's data.
 * A recruiter who was created with a client is limited to it too. Admins, and recruiters with no client, see all clients.
 */
public final class ClientAccess {

    private ClientAccess() { }

    public static boolean isLimitedToOneClient(AuthUser user) {
        return user != null
                && (user.role() == Role.HIRING_MANAGER || user.role() == Role.MANAGER || user.role() == Role.HR_BP
                || (user.role() == Role.RECRUITER && user.clientId() != null));
    }

    public static void require(AuthUser user, Long clientId) {
        if (isLimitedToOneClient(user) && !Objects.equals(user.clientId(), clientId)) {
            throw new AccessDeniedException("You can only access your own client's data");
        }
    }
}
