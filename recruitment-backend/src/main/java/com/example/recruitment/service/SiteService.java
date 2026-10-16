package com.example.recruitment.service;

import com.example.recruitment.dto.FooterRequest;
import com.example.recruitment.dto.SiteSettingsRequest;
import com.example.recruitment.dto.SiteSettingsResponse;
import com.example.recruitment.entity.Client;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * What a client's website editor (role CLIENT_ADMIN) may change. The client is always taken from the
 * logged-in user, never from the request, so nobody can edit another client's site.
 * Colours, theme, stages, status, slug and jobs are deliberately not reachable from here.
 */
@Service
@Transactional
public class SiteService {

    private final ClientRepository clients;
    private final FileStorageService fileStorage;

    public SiteService(ClientRepository clients, FileStorageService fileStorage) {
        this.clients = clients;
        this.fileStorage = fileStorage;
    }

    @Transactional(readOnly = true)
    public SiteSettingsResponse get(AuthUser user) {
        return SiteSettingsResponse.from(own(user));
    }

    public SiteSettingsResponse update(AuthUser user, SiteSettingsRequest request) {
        Client client = own(user);
        PortalGuard.requireActive(client); // a frozen portal takes no changes
        client.setApplicationName(request.applicationName().trim());
        client.setDescription(blankToNull(request.description()));
        FooterRequest f = request.footer();
        client.setFooterCompanyName(blankToNull(f.companyName()));
        client.setFooterAddress(blankToNull(f.address()));
        client.setFooterEmail(blankToNull(f.contactEmail()));
        client.setFooterPhone(blankToNull(f.phone()));
        client.setFooterWebsite(blankToNull(f.websiteUrl()));
        client.setFooterCopyright(blankToNull(f.copyrightText()));
        client.setFooterText(blankToNull(f.customText()));
        return SiteSettingsResponse.from(client);
    }

    public SiteSettingsResponse uploadLogo(AuthUser user, MultipartFile file) {
        Client client = own(user);
        PortalGuard.requireActive(client);
        String oldUrl = client.getLogoUrl();
        client.setLogoUrl(fileStorage.saveLogo(file));
        fileStorage.delete(oldUrl);
        return SiteSettingsResponse.from(client);
    }

    public SiteSettingsResponse removeLogo(AuthUser user) {
        Client client = own(user);
        PortalGuard.requireActive(client);
        fileStorage.delete(client.getLogoUrl());
        client.setLogoUrl(null);
        return SiteSettingsResponse.from(client);
    }

    private Client own(AuthUser user) {
        if (user.clientId() == null) throw new ResourceNotFoundException("No website is linked to this login");
        return clients.findById(user.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Website not found"));
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}