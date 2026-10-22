package com.cashflow.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds bodies before JSON parsing and rate-limits authentication by direct peer IP. */
public class RequestGuard extends OncePerRequestFilter {
    private record Window(long start,int count) {}
    private final Map<String,Window> windows=new HashMap<>();
    private synchronized boolean allow(String ip) {
        long now=Instant.now().getEpochSecond();
        windows.entrySet().removeIf(e->now-e.getValue().start>900);
        Window w=windows.get(ip);
        if(w==null) { if(windows.size()>=10000) return false; w=new Window(now,0); }
        windows.put(ip,new Window(w.start,w.count+1)); return w.count<40;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(request.getMethod().equals("POST") && request.getRequestURI().startsWith("/api/v1/auth/") && !allow(request.getRemoteAddr())) {
            response.setHeader("Retry-After","900"); fail(response,429,"Too many attempts. Retry in 15 minutes."); return;
        }
        if(Set.of("POST","PATCH","PUT").contains(request.getMethod()) && request.getRequestURI().startsWith("/api/")) {
            byte[] body=request.getInputStream().readNBytes(65537);
            if(body.length>65536) { fail(response,413,"Request body exceeds 64 KB."); return; }
            HttpServletRequestWrapper wrapped=new HttpServletRequestWrapper(request) {
                @Override public ServletInputStream getInputStream() {
                    var input=new ByteArrayInputStream(body);
                    return new ServletInputStream() {
                        public int read() { return input.read(); }
                        public boolean isFinished() { return input.available()==0; }
                        public boolean isReady() { return true; }
                        public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                    };
                }
                @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8)); }
            };
            chain.doFilter(wrapped,response); return;
        }
        chain.doFilter(request,response);
    }
    public static void fail(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        response.getWriter().write("{\"status\":"+status+",\"message\":\""+message+"\",\"fieldErrors\":{}}");
    }
}
