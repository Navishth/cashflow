# Security decisions and boundaries

- Passwords: BCrypt cost 12. Verification required for password login. Maximum 72 UTF-8 bytes avoids BCrypt truncation.
- Sessions: server-side, 30-minute idle timeout, HttpOnly cookie, SameSite=Lax; Secure enforced when production flag is enabled. Login changes the session ID and clears CSRF state. Logout destroys the session.
- Password reset: increments a user authentication version. Each authenticated request checks it, invalidating previously issued sessions across browsers.
- Verification/reset: 256-bit random tokens, SHA-256 database hashes, 24-hour/30-minute expiry, single-use under a pessimistic row lock. One outstanding token per user/purpose after resend. The `auth_tokens` table intentionally unifies the two token types with a constrained kind field.
- Token links use fragments, not query parameters, to avoid normal server access logs and Referer leakage. The frontend removes the fragment after reading it.
- CSRF: Spring Security's session-backed repository and default masked request handler remain enabled. The JSON bootstrap exposes only a CSRF token; authentication cookies never become JavaScript-readable.
- CORS: exact configurable origins; wildcard origins are rejected. Credentials are allowed only for those configured origins.
- Google: standard Spring OAuth2/OIDC authorization code flow, provider subject as identity. Verified provider email required. Existing email accounts are never automatically linked. Redirect target is server configuration, never user input.
- Ownership: resolve authenticated user, then owned business. All resource queries constrain ownership. Foreign resources return 404. DB composite keys enforce same-business recurrence links and scenario baselines.
- Input: Bean Validation, domain checks, positive decimal amounts, bounded dates, list/page limits, 64 KB backend bodies and 128 KB inference bodies.
- Authentication abuse: 40 sensitive POST requests per direct-peer IP per 15 minutes, bounded in-memory buckets. This includes registration and recovery. Behind a proxy, configure edge rate limits too. The application deliberately does not blindly trust X-Forwarded-For.
- Inference: no DB access, no browser route, private deployment network, shared internal key with constant-time comparison, request/response validation, timeouts, explicit fallback.
- Local mail: ignored filesystem spool accessible to the local machine user; never publicly serve it. File delivery is rejected in production mode. Actual SMTP requires environment credentials.
- Logging: no passwords, session IDs, raw verification/reset tokens, or flow data in application logs. SQL exception parameter logging is suppressed; internal errors expose only safe messages. Keep reverse-proxy logs free of request bodies.
- Deployment: containers use non-root backend/Python users. Nginx sets CSP, frame denial, nosniff, and no-referrer headers for the UI.

This is a tested local MVP, not a certified or independently audited financial system. Sessions and rate limits are in one process; use a shared session/rate-limit store before adding replicas. No bank/payment integration exists. Account deletion/export, retention enforcement, production monitoring/backups, and independent penetration testing remain deployment responsibilities.

Production credentials, SMTP delivery, external Google OAuth, HTTPS ingress, and Docker runtime must be verified in the actual target environment. Dependency scan results and execution evidence are recorded in IMPLEMENTATION_STATUS.md; a clean scan does not prove the absence of vulnerabilities.

References used: [Spring Security 6.5 CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html), [Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/), [Springdoc compatibility](https://springdoc.org/).

## Dependency review — 21 October 2026

Runtime overrides keep the requested Spring Boot 3 stack while applying available fixes: Jackson BOM 2.21.7, Commons Lang 3.18.0, Log4j 2.25.5, Tomcat 10.1.61 and PostgreSQL JDBC 42.7.14. These overrides require the same build/integration regression checks as framework updates.

Two OSV advisories affect Spring Web MVC 6.2.19's optional rendering features:

- [GHSA-j9f9-w8pj-32f8](https://osv.dev/vulnerability/GHSA-j9f9-w8pj-32f8): SSE streams with view fragments. This application uses JSON REST responses and has no SSE or fragment-rendering endpoints.
- [GHSA-pc63-qcmh-9cmg](https://osv.dev/vulnerability/GHSA-pc63-qcmh-9cmg): XsltView with implicit view-name resolution and a wildcard mapping. This application configures no XsltView/XsltViewResolver or server-side view rendering; React is served separately.

These are dependency findings retained by the scanner, not silently waived. Based on the current code/configuration, their required features are absent. Maven Central's newest public 6.2 release at inspection was 6.2.19. A future rendering feature or framework upgrade must revisit this assessment. Obtain an appropriate vendor patch/support plan or reassess migration before production if an organization requires zero outstanding dependency advisories. This review is not an independent penetration test.
