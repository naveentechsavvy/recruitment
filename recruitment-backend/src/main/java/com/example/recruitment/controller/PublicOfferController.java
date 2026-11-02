package com.example.recruitment.controller;

import com.example.recruitment.dto.OfferPublicView;
import com.example.recruitment.dto.OfferResponseRequest;
import com.example.recruitment.service.OfferService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (no login, see SecurityConfig): the candidate opens the secure link from the offer email.
 * GET only shows the offer and never changes anything. The answer is a separate POST that the candidate confirms on the page.
 */
@RestController
@RequestMapping("/api/public/offers")
public class PublicOfferController {

    private final OfferService offers;

    public PublicOfferController(OfferService offers) {
        this.offers = offers;
    }

    @GetMapping("/{token}")
    public OfferPublicView view(@PathVariable String token) {
        return offers.describe(token);
    }

    @PostMapping("/{token}/respond")
    public OfferPublicView respond(@PathVariable String token, @RequestBody OfferResponseRequest request) {
        String decision = request == null || request.decision() == null ? "" : request.decision().trim().toUpperCase();
        if (!decision.equals("ACCEPT") && !decision.equals("DECLINE")) {
            throw new IllegalArgumentException("Please choose Accept or Reject");
        }
        return offers.respond(token, decision.equals("ACCEPT"), request.reason());
    }
}
