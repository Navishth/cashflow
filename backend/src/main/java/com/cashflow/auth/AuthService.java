package com.cashflow.auth;

import com.cashflow.common.ApiException;
import com.cashflow.data.Models.*;
import com.cashflow.data.Repositories.*;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final Users users; private final Tokens tokens; private final Identities identities;
    private final PasswordEncoder encoder; private final MailDelivery mail;
    private final String dummyHash;
    public AuthService(Users users,Tokens tokens,Identities identities,PasswordEncoder encoder,MailDelivery mail) {
        this.users=users; this.tokens=tokens; this.identities=identities; this.encoder=encoder; this.mail=mail;
        dummyHash=encoder.encode(UUID.randomUUID().toString());
    }
    public record UserView(UUID id,String email,String displayName,boolean verified) {}
    public static UserView view(User u) { return new UserView(u.id,u.email,u.displayName,u.verified); }
    public static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    public UUID userId() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !(auth.getPrincipal() instanceof SessionUser u)) throw new ApiException(401,"Sign in to continue.");
        return u.id();
    }
    public UserView me() { return view(users.findById(userId()).orElseThrow(ApiException::notFound)); }
    private void passwordPolicy(String password) {
        ApiException.require(password.length()>=12 && password.getBytes(StandardCharsets.UTF_8).length<=72,"Use a password of at least 12 characters and at most 72 UTF-8 bytes.");
    }
    @Transactional public void register(String email,String password,String name) {
        passwordPolicy(password); email=normalize(email);
        if(users.findByEmail(email).isPresent()) return;
        var u=new User(); u.email=email; u.passwordHash=encoder.encode(password); u.displayName=name.trim();
        users.save(u); issue(u,"VERIFY");
    }
    private void issue(User user,String kind) {
        tokens.deleteByUserIdAndKind(user.id,kind); tokens.flush();
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var token=new Token(); token.userId=user.id; token.kind=kind; token.tokenHash=hash(raw);
        token.expiresAt=Instant.now().plus("VERIFY".equals(kind)?Duration.ofHours(24):Duration.ofMinutes(30));
        tokens.save(token); mail.send(user.email,kind,raw);
    }
    @Transactional public void recover(String email,boolean verification) {
        users.findByEmail(normalize(email)).ifPresent(u->{
            if(verification && !u.verified) issue(u,"VERIFY");
            else if(!verification && u.passwordHash!=null && u.verified) issue(u,"RESET");
        });
    }
    private Token consume(String raw,String kind) {
        var token=tokens.findByTokenHashAndKind(hash(raw),kind).orElseThrow(()->new ApiException(400,"This link is invalid or expired."));
        if(token.usedAt!=null || !token.expiresAt.isAfter(Instant.now())) throw new ApiException(400,"This link is invalid or expired.");
        token.usedAt=Instant.now(); return token;
    }
    @Transactional public void verify(String raw) {
        var token=consume(raw,"VERIFY"); var user=users.findById(token.userId).orElseThrow(ApiException::notFound); user.verified=true;
    }
    @Transactional public void reset(String raw,String password) {
        passwordPolicy(password); var token=consume(raw,"RESET");
        var user=users.findById(token.userId).orElseThrow(ApiException::notFound);
        user.passwordHash=encoder.encode(password); user.authVersion++;
    }
    public UserView login(String email,String password,HttpServletRequest req,HttpServletResponse res) {
        var user=users.findByEmail(normalize(email)).orElse(null);
        boolean matches=encoder.matches(password,user==null||user.passwordHash==null ? dummyHash:user.passwordHash);
        if(!matches || user==null || !user.verified || user.passwordHash==null) throw new ApiException(401,"Email or password is incorrect, or email is not verified.");
        install(user,req,res); return view(user);
    }
    public void install(User user,HttpServletRequest req,HttpServletResponse res) {
        var auth=UsernamePasswordAuthenticationToken.authenticated(new SessionUser(user.id,user.authVersion),null,List.of(()->"ROLE_USER"));
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(auth,req,res);
        new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository()).onAuthentication(auth,req,res);
        var context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth); SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context,req,res);
    }
    @Transactional public User resolveGoogle(OidcUser oidc) {
        if(!Boolean.TRUE.equals(oidc.getEmailVerified())) throw new ApiException(400,"Google must verify your email first.");
        var identity=identities.findByProviderAndSubject("google",oidc.getSubject());
        if(identity.isPresent()) return users.findById(identity.get().userId).orElseThrow(ApiException::notFound);
        String email=normalize(oidc.getEmail());
        if(users.findByEmail(email).isPresent()) throw new ApiException(409,"An account already uses this email. Sign in using its original method.");
        var user=new User(); user.email=email; user.displayName=Optional.ofNullable(oidc.getFullName()).orElse("Business owner"); user.verified=true; users.save(user);
        var link=new Identity(); link.userId=user.id; link.provider="google"; link.subject=oidc.getSubject(); identities.save(link);
        return user;
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
