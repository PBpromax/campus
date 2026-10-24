package com.example.campushelp.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component @Order(Ordered.HIGHEST_PRECEDENCE) @RequiredArgsConstructor @Slf4j
public class RequestTraceFilter extends OncePerRequestFilter {
    private final ObjectMapper json;
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        String trace=RequestTrace.normalize(request.getHeader(RequestTrace.TRACE_HEADER));
        String span=UUID.randomUUID().toString();long started=System.nanoTime();var logged=new AtomicBoolean();
        response.setHeader(RequestTrace.TRACE_HEADER,trace);response.setHeader(RequestTrace.REQUEST_HEADER,span);
        Runnable finish=()->{
            if(!logged.compareAndSet(false,true)) return;
            // Use the handler template, never raw URI/query, JWT, messages or request/response bodies.
            Object pattern=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            try { log.info("campus_observation {}",json.writeValueAsString(Map.of("kind","http","traceId",trace,"requestId",span,
                "method",request.getMethod(),"route",pattern==null?"UNMATCHED":pattern.toString(),"status",response.getStatus(),
                "durationMs",(System.nanoTime()-started)/1_000_000))); }
            catch(Exception ignored) { log.warn("HTTP observation serialization failed"); }
        };
        try(var scope=RequestTrace.use(Map.of("traceId",trace,"requestId",span))) {
            chain.doFilter(request,response);
        } finally {
            if(request.isAsyncStarted()) {
                var listener=new AsyncListener() {
                    public void onComplete(AsyncEvent e) { finish.run(); }
                    public void onTimeout(AsyncEvent e) { /* SSE transport status is not model success; wait for completion. */ }
                    public void onError(AsyncEvent e) { /* Completion logs once; no exception text or bodies. */ }
                    public void onStartAsync(AsyncEvent e) { e.getAsyncContext().addListener(this); }
                };
                try { request.getAsyncContext().addListener(listener); }
                catch(IllegalStateException completed) { finish.run(); }
            } else finish.run();
        }
    }
}
