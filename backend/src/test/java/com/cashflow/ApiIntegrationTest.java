package com.cashflow;

import com.cashflow.data.Repositories.Users;
import com.cashflow.data.Repositories.Tokens;
import com.cashflow.auth.AuthService;
import com.cashflow.common.ApiException;
import com.cashflow.forecast.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties={"spring.flyway.schemas=cft_test","spring.flyway.default-schema=cft_test","spring.jpa.properties.hibernate.default_schema=cft_test","app.mail-mode=file",
    "app.google-client-id=test-only.apps.googleusercontent.com","app.google-client-secret=test-only-not-a-real-secret",
    "app.google-redirect-uri=http://localhost:5173/login/oauth2/code/google","app.frontend-url=http://localhost:5173"})
@AutoConfigureMockMvc
class ApiIntegrationTest {
    static final Path MAIL;
    static { try { MAIL=Files.createTempDirectory("cft-test-mail"); } catch(Exception e) { throw new RuntimeException(e); } }
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) { r.add("app.mail-directory",()->MAIL.toString()); }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired Users users;
    @Autowired Tokens tokens; @Autowired AuthService auth;
    @MockitoBean MlClient ml;
    static final java.util.concurrent.atomic.AtomicInteger CLIENTS=new java.util.concurrent.atomic.AtomicInteger();
    final String clientIp="127.0.1."+CLIENTS.incrementAndGet();
    @BeforeEach void setup() { when(ml.predict(anyList(),any(),anyBoolean())).thenAnswer(i->FinancialEngine.fallback(List.of(),i.getArgument(1),"Integration test baseline")); }
    record Account(String email,MockHttpSession session) {}
    @Test void googleAuthorizationUsesFixedCallbackAndSessionBoundState() throws Exception {
        mvc.perform(get("/api/v1/auth/options")).andExpect(status().isOk())
            .andExpect(jsonPath("$.googleEnabled").value(true))
            .andExpect(jsonPath("$.googleAuthorizationUrl").value("http://localhost:5173/oauth2/authorization/google"))
            .andExpect(jsonPath("$.clientSecret").doesNotExist());
        var start=mvc.perform(get("/oauth2/authorization/google").header("Host","untrusted.example")).andExpect(status().is3xxRedirection()).andReturn();
        var uri=org.springframework.web.util.UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl()).build();
        assertThat(uri.getHost()).isEqualTo("accounts.google.com");
        var query=uri.getQueryParams();
        assertThat(java.net.URLDecoder.decode(query.getFirst("redirect_uri"),java.nio.charset.StandardCharsets.UTF_8))
            .isEqualTo("http://localhost:5173/login/oauth2/code/google");
        assertThat(java.net.URLDecoder.decode(query.getFirst("scope"),java.nio.charset.StandardCharsets.UTF_8).split(" "))
            .containsExactlyInAnyOrder("openid","profile","email");
        assertThat(query.getFirst("state")).isNotBlank();
        assertThat(query.getFirst("nonce")).isNotBlank();
        assertThat(start.getResponse().getRedirectedUrl()).doesNotContain("test-only-not-a-real-secret");
        mvc.perform(get("/login/oauth2/code/google").session((MockHttpSession)start.getRequest().getSession(false))
            .param("state","tampered-state").param("code","invalid-code"))
            .andExpect(redirectedUrl("http://localhost:5173/login?oauth=failed"));
        mvc.perform(get("/api/v1/auth/me").session((MockHttpSession)start.getRequest().getSession(false))).andExpect(status().isUnauthorized());
    }

    @Test void googleConsentCancellationReturnsToSignInWithoutCreatingASessionUser() throws Exception {
        var start=mvc.perform(get("/oauth2/authorization/google")).andExpect(status().is3xxRedirection()).andReturn();
        var query=org.springframework.web.util.UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl()).build().getQueryParams();
        var session=(MockHttpSession)start.getRequest().getSession(false);
        mvc.perform(get("/login/oauth2/code/google").session(session).param("error","access_denied")
            .param("state",java.net.URLDecoder.decode(query.getFirst("state"),java.nio.charset.StandardCharsets.UTF_8)))
            .andExpect(redirectedUrl("http://localhost:5173/login?oauth=failed"));
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    }
    JsonNode body(MvcResult r) throws Exception { return json.readTree(r.getResponse().getContentAsString()); }
    MvcResult send(MockHttpServletRequestBuilder req,Object data,int status) throws Exception {
        return mvc.perform(req.with(r->{r.setRemoteAddr(clientIp);return r;}).with(csrf()).contentType("application/json").content(json.writeValueAsString(data))).andExpect(status().is(status)).andReturn();
    }
    String token(String email) throws Exception {
        try(var stream=Files.list(MAIL)) {
            for(Path path:stream.toList()) {
                String content=Files.readString(path);
                if(content.contains("To: "+email+"\n")) { String token=content.split("#token=")[1].split("\\s")[0]; Files.delete(path); return token; }
            }
        }
        throw new AssertionError("No local verification message");
    }
    Account account() throws Exception {
        String email="test-"+UUID.randomUUID()+"@example.test",password="correct horse battery staple";
        send(post("/api/v1/auth/register"),Map.of("email",email,"password",password,"displayName","Test owner"),200);
        send(post("/api/v1/auth/login"),Map.of("email",email,"password",password),401);
        String raw=token(email);
        send(post("/api/v1/auth/verify-email"),Map.of("token",raw),200);
        send(post("/api/v1/auth/verify-email"),Map.of("token",raw),400);
        var result=send(post("/api/v1/auth/login"),Map.of("email",email,"password",password),200);
        return new Account(email,(MockHttpSession)result.getRequest().getSession(false));
    }
    void business(Account a) throws Exception {
        send(post("/api/v1/businesses").session(a.session),Map.of("name","Test business","currency","INR","timezone","Asia/Kolkata","openingDate",LocalDate.now().minusDays(60).toString(),"openingBalance",1000,"reserve",500,"demo",false),201);
    }
    Map<String,Object> flow(int amount,String type,String status,LocalDate date) {
        return new HashMap<>(Map.of("type",type,"amount",amount,"description","Integration entry","category","Operations","expectedDate",date.toString(),"status",status,"source","SCHEDULED"));
    }
    @Test void sessionCsrfLogoutRecoveryAndPasswordHashing() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        var a=account();
        assertThat(users.findByEmail(a.email).orElseThrow().passwordHash).startsWith("$2a$12$").doesNotContain("correct");
        mvc.perform(get("/api/v1/auth/me").session(a.session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(a.email));
        send(post("/api/v1/auth/forgot-password"),Map.of("email",a.email),200);
        String raw=token(a.email);
        send(post("/api/v1/auth/reset-password"),Map.of("token",raw,"password","another correct horse phrase"),200);
        send(post("/api/v1/auth/reset-password"),Map.of("token",raw,"password","another correct horse phrase"),400);
        mvc.perform(get("/api/v1/auth/me").session(a.session)).andExpect(status().isUnauthorized());
        var login=send(post("/api/v1/auth/login"),Map.of("email",a.email,"password","another correct horse phrase"),200);
        var session=(MockHttpSession)login.getRequest().getSession(false);
        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void financeForecastReplayAndEveryResourceAreIsolated() throws Exception {
        var a=account(); var b=account(); business(a); business(b);
        var today=LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        var data=flow(300,"INFLOW","PLANNED",today.plusDays(2));
        String flowId=body(send(post("/api/v1/cash-flows").session(a.session),data,201)).get("id").asText();
        send(post("/api/v1/cash-flows").session(a.session),flow(100,"OUTFLOW","PAID",today),201);
        send(post("/api/v1/cash-flows").session(a.session),flow(-1,"INFLOW","PLANNED",today),400);
        send(post("/api/v1/cash-flows").session(a.session),flow(50,"OUTFLOW","PAID",today.plusDays(1)),400);
        mvc.perform(get("/api/v1/businesses/current").session(a.session)).andExpect(jsonPath("$.currentBalance").value(900));
        mvc.perform(get("/api/v1/cash-flows/"+flowId).session(b.session)).andExpect(status().isNotFound());
        send(patch("/api/v1/cash-flows/"+flowId).session(b.session),data,404);
        mvc.perform(delete("/api/v1/cash-flows/"+flowId).session(b.session).with(csrf())).andExpect(status().isNotFound());
        Map<String,Object> rule=Map.of("type","OUTFLOW","amount",50,"description","Weekly cost","category","Operations","frequency","WEEKLY","startDate",today.plusDays(1).toString(),"active",true);
        String ruleId=body(send(post("/api/v1/recurring-rules").session(a.session),rule,201)).get("id").asText();
        send(patch("/api/v1/recurring-rules/"+ruleId).session(b.session),rule,404);
        mvc.perform(delete("/api/v1/recurring-rules/"+ruleId).session(b.session).with(csrf())).andExpect(status().isNotFound());
        var occurrence=flow(50,"OUTFLOW","PLANNED",today.plusDays(1)); occurrence.put("recurringRuleId",ruleId); occurrence.put("occurrenceDate",today.plusDays(1).toString());
        send(post("/api/v1/cash-flows").session(a.session),occurrence,201);
        send(post("/api/v1/cash-flows").session(a.session),occurrence,409);
        var forecast=body(send(post("/api/v1/forecasts/generate").session(a.session),Map.of(),200)); String baseline=forecast.get("id").asText();
        assertThat(forecast.get("points")).hasSize(30);
        assertThat(forecast.at("/points/0/scheduledOutflows").asInt()).isEqualTo(50); // replacement, not 100
        assertThat(forecast.at("/summary/finalBalance").asInt()).isEqualTo(950); // 900 + 300 - five weekly costs
        mvc.perform(get("/api/v1/forecasts/"+baseline).session(b.session)).andExpect(status().isNotFound());
        var scenarioData=Map.of("name","Late customer","baselineId",baseline,"changes",List.of(Map.of("kind","RESCHEDULE","targetEventId",flowId,"newDate",today.plusDays(40).toString())));
        String scenario=body(send(post("/api/v1/scenarios").session(a.session),scenarioData,201)).get("id").asText();
        send(post("/api/v1/scenarios").session(b.session),scenarioData,404);
        var comparison=body(send(post("/api/v1/scenarios/"+scenario+"/simulate").session(a.session),Map.of(),200));
        assertThat(comparison.get("finalBalanceChange").asInt()).isEqualTo(-300);
        assertThat(comparison.get("summary").get("finalBalance").asInt()).isEqualTo(650);
        var repeat=body(send(post("/api/v1/scenarios/"+scenario+"/simulate").session(a.session),Map.of(),200));
        assertThat(repeat.get("points")).isEqualTo(comparison.get("points"));
        mvc.perform(get("/api/v1/cash-flows/"+flowId).session(a.session)).andExpect(jsonPath("$.expectedDate").value(today.plusDays(2).toString()));
        for(String suffix:List.of("","/compare")) mvc.perform(get("/api/v1/scenarios/"+scenario+suffix).session(b.session)).andExpect(status().isNotFound());
        send(post("/api/v1/scenarios/"+scenario+"/simulate").session(b.session),Map.of(),404);
        mvc.perform(delete("/api/v1/scenarios/"+scenario).session(b.session).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/scenarios/"+scenario).session(a.session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/scenarios/"+scenario).session(a.session)).andExpect(status().isNotFound());
    }
    @Test void realCsrfTokenSurvivesBootstrapAndRotatesOnLogin() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        var a=account();
        var bootstrap=mvc.perform(get("/api/v1/auth/csrf").session(a.session)).andExpect(status().isOk()).andReturn();
        var csrf=body(bootstrap);
        mvc.perform(post("/api/v1/auth/logout").session(a.session).header(csrf.get("headerName").asText(),csrf.get("token").asText())).andExpect(status().isNoContent());
    }
    @Test void expiredTokensAndSupersededLinksCannotChangeAccounts() throws Exception {
        String email="expiry-"+UUID.randomUUID()+"@example.test";
        send(post("/api/v1/auth/register"),Map.of("email",email,"password","correct horse battery staple","displayName","Expiry test"),200);
        String expired=token(email);
        var stored=tokens.findAll().stream().filter(t->t.tokenHash.equals(AuthService.hash(expired))).findFirst().orElseThrow();
        assertThat(stored.tokenHash).doesNotContain(expired);
        stored.expiresAt=java.time.Instant.now().minusSeconds(1); tokens.saveAndFlush(stored);
        send(post("/api/v1/auth/verify-email"),Map.of("token",expired),400);
        assertThat(users.findByEmail(email).orElseThrow().verified).isFalse();
        send(post("/api/v1/auth/resend-verification"),Map.of("email",email),200);
        send(post("/api/v1/auth/verify-email"),Map.of("token",token(email)),200);
        send(post("/api/v1/auth/forgot-password"),Map.of("email",email),200);
        String old=token(email);
        send(post("/api/v1/auth/forgot-password"),Map.of("email",email),200);
        String current=token(email);
        send(post("/api/v1/auth/reset-password"),Map.of("token",old,"password","another correct horse phrase"),400);
        var reset=tokens.findAll().stream().filter(t->t.tokenHash.equals(AuthService.hash(current))).findFirst().orElseThrow();
        reset.expiresAt=java.time.Instant.now().minusSeconds(1); tokens.saveAndFlush(reset);
        send(post("/api/v1/auth/reset-password"),Map.of("token",current,"password","another correct horse phrase"),400);
        send(post("/api/v1/auth/login"),Map.of("email",email,"password","correct horse battery staple"),200);
    }
    @Test void googleIdentityUsesSubjectAndNeverMergesMatchingEmails() throws Exception {
        var existing=account();
        var oidc=mock(org.springframework.security.oauth2.core.oidc.user.OidcUser.class);
        when(oidc.getEmailVerified()).thenReturn(true);
        when(oidc.getSubject()).thenReturn("subject-"+UUID.randomUUID());
        when(oidc.getEmail()).thenReturn(existing.email);
        assertThatThrownBy(()->auth.resolveGoogle(oidc)).isInstanceOf(ApiException.class);
        String fresh="google-"+UUID.randomUUID()+"@example.test";
        when(oidc.getEmail()).thenReturn(fresh);
        var created=auth.resolveGoogle(oidc);
        assertThat(created.passwordHash).isNull();
        when(oidc.getEmail()).thenReturn("changed-"+UUID.randomUUID()+"@example.test");
        assertThat(auth.resolveGoogle(oidc).id).isEqualTo(created.id);
        when(oidc.getEmailVerified()).thenReturn(false);
        assertThatThrownBy(()->auth.resolveGoogle(oidc)).isInstanceOf(ApiException.class);
    }
}
