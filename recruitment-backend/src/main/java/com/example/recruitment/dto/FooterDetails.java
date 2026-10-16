package com.example.recruitment.dto;

import com.example.recruitment.entity.Client;

/** The footer of a client's application as the API returns it. Empty fields are null, never "". */
public record FooterDetails(
        String companyName,
        String address,
        String contactEmail,
        String phone,
        String websiteUrl,
        String copyrightText,
        String customText
) {
    public static FooterDetails from(Client c) {
        return new FooterDetails(c.getFooterCompanyName(), c.getFooterAddress(), c.getFooterEmail(),
                c.getFooterPhone(), c.getFooterWebsite(), c.getFooterCopyright(), c.getFooterText());
    }
}
