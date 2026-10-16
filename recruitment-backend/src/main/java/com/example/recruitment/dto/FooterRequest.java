package com.example.recruitment.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Footer details of the public application. Every field is optional: send an empty or blank value to clear one.
 * (When the whole "footer" object is left out of a client update, the saved footer is kept as it is.)
 */
public record FooterRequest(
        @Size(max = 150, message = "Company name must be at most 150 characters")
        String companyName,

        @Size(max = 300, message = "Address must be at most 300 characters")
        String address,

        @Size(max = 150, message = "Contact email must be at most 150 characters")
        @Pattern(regexp = "^\\s*$|^\\s*[^@\\s]+@[^@\\s]+\\.[^@\\s]+\\s*$", message = "Please enter a valid contact email")
        String contactEmail,

        @Size(max = 30, message = "Phone number must be at most 30 characters")
        @Pattern(regexp = "^\\s*$|^\\s*[+0-9 ()\\-.]{3,30}\\s*$", message = "Phone number can only contain digits, spaces, + ( ) - and .")
        String phone,

        @Size(max = 200, message = "Website must be at most 200 characters")
        @Pattern(regexp = "^\\s*$|^\\s*https?://[^\\s/$.?#][^\\s]*\\.[^\\s]+\\s*$", message = "Website must start with http:// or https://")
        String websiteUrl,

        @Size(max = 200, message = "Copyright text must be at most 200 characters")
        String copyrightText,

        @Size(max = 500, message = "Custom footer text must be at most 500 characters")
        String customText
) { }
