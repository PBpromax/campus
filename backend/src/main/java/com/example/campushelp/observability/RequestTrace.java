package com.example.campushelp.observability;

import org.slf4j.MDC;
import java.util.Map;
import java.util.UUID;

/** Correlation identifiers are untrusted diagnostics, never authorization or idempotency keys. */
public final class RequestTrace {
    public static final String TRACE_HEADER="X-Campus-Trace-Id", REQUEST_HEADER="X-Request-Id";
    private RequestTrace() {}
    public static String traceId() { return MDC.get("traceId"); }
    public static String requestId() { return MDC.get("requestId"); }
    public static String normalize(String candidate) {
        if(candidate!=null && candidate.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) return candidate.toLowerCase(java.util.Locale.ROOT);
        return UUID.randomUUID().toString();
    }
    public static Scope use(Map<String,String> context) { return new Scope(context); }
    public static final class Scope implements AutoCloseable {
        private final Map<String,String> previous=MDC.getCopyOfContextMap();
        private Scope(Map<String,String> context) { if(context==null) MDC.clear(); else MDC.setContextMap(context); }
        @Override public void close() { if(previous==null) MDC.clear(); else MDC.setContextMap(previous); }
    }
}
