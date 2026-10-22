package com.cashflow;

import com.cashflow.auth.RequestGuard;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class RequestGuardTest {
    @Test void forwardedHeaderCannotBypassRateLimit() throws Exception {
        var guard=new RequestGuard();
        var allowed=new AtomicInteger();
        for(int i=0;i<41;i++) {
            var request=new MockHttpServletRequest("POST","/api/v1/auth/login");
            request.setRemoteAddr("192.0.2.1");
            request.addHeader("X-Forwarded-For","198.51.100."+i);
            var response=new MockHttpServletResponse();
            guard.doFilter(request,response,(r,s)->allowed.incrementAndGet());
            if(i==40) {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(response.getHeader("Retry-After")).isEqualTo("900");
            }
        }
        assertThat(allowed).hasValue(40);
    }
    @Test void oversizedJsonIsRejectedBeforeParsing() throws Exception {
        var request=new MockHttpServletRequest("PATCH","/api/v1/businesses/current");
        request.setContent(new byte[65537]);
        var response=new MockHttpServletResponse();
        new RequestGuard().doFilter(request,response,(r,s)->{throw new AssertionError("Oversized body passed through");});
        assertThat(response.getStatus()).isEqualTo(413);
    }
}
