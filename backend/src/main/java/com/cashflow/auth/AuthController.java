package com.cashflow.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service; private final GoogleOAuthConfig google;
    public AuthController(AuthService service,GoogleOAuthConfig google) { this.service=service; this.google=google; }
    public record Registration(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(min=12,max=72) String password,@NotBlank @Size(max=100) String displayName) {}
    public record Login(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=72) String password) {}
    public record EmailRequest(@NotBlank @Email @Size(max=254) String email) {}
    public record TokenRequest(@NotBlank @Size(max=100) String token) {}
    public record Reset(@NotBlank @Size(max=100) String token,@NotBlank @Size(min=12,max=72) String password) {}
    private Map<String,String> accepted() { return Map.of("message","If the account is eligible, a message has been queued. Check your email."); }
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    public record Options(boolean googleEnabled,String googleAuthorizationUrl) {}
    @GetMapping("/options") Options options() { return new Options(google.enabled(),google.authorizationUrl()); }
    @PostMapping("/register") Map<String,String> register(@Valid @RequestBody Registration r) { service.register(r.email,r.password,r.displayName); return accepted(); }
    @PostMapping("/verify-email") Map<String,String> verify(@Valid @RequestBody TokenRequest r) { service.verify(r.token); return Map.of("message","Email verified. You can now sign in."); }
    @PostMapping("/login") AuthService.UserView login(@Valid @RequestBody Login r,HttpServletRequest req,HttpServletResponse res) { return service.login(r.email,r.password,req,res); }
    @GetMapping("/me") AuthService.UserView me() { return service.me(); }
    @PostMapping("/forgot-password") Map<String,String> forgot(@Valid @RequestBody EmailRequest r) { service.recover(r.email,false); return accepted(); }
    @PostMapping("/resend-verification") Map<String,String> resend(@Valid @RequestBody EmailRequest r) { service.recover(r.email,true); return accepted(); }
    @PostMapping("/reset-password") Map<String,String> reset(@Valid @RequestBody Reset r) { service.reset(r.token,r.password); return Map.of("message","Password changed. Sign in again on all devices."); }
}
