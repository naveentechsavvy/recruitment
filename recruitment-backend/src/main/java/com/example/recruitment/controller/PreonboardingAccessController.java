package com.example.recruitment.controller;
import com.example.recruitment.dto.PreonboardingPublicView;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.service.PreonboardingAccessService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class PreonboardingAccessController {
 private final PreonboardingAccessService service;
 public PreonboardingAccessController(PreonboardingAccessService service){this.service=service;}
 @PostMapping("/api/site/preonboarding/candidates/{candidateId}/invite")
 public Map<String,String> invite(@PathVariable Long candidateId,@AuthenticationPrincipal AuthUser user){return Map.of("message",service.invite(candidateId,user));}
 @GetMapping("/api/public/preonboarding/{token}")
 public PreonboardingPublicView describe(@PathVariable String token){return service.describe(token);}
 @PostMapping("/api/public/preonboarding/{token}/otp")
 public PreonboardingPublicView otp(@PathVariable String token){return service.requestOtp(token);}
 @PostMapping("/api/public/preonboarding/{token}/verify")
 public PreonboardingPublicView verify(@PathVariable String token,@RequestBody Map<String,String> body){return service.verify(token,body.get("otp"));}
 @PostMapping("/api/public/preonboarding/{token}/submit")
 public PreonboardingPublicView submit(@PathVariable String token,@RequestBody Map<String,String> body){return service.submit(token,body.get("details"));}
}
