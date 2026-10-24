package com.example.campushelp.ai;

import java.io.IOException;

/** Stable internal categories; never include provider response bodies or credentials. */
public class ModelFailure extends IOException {
    public enum Kind { TIMEOUT, CLIENT_CANCELLED, NETWORK, UPSTREAM_RATE_LIMIT, UPSTREAM_AUTH, UPSTREAM_5XX, UPSTREAM_REJECTED, INVALID_RESPONSE }
    private final Kind kind;
    public ModelFailure(Kind kind) {super("Model call failed: "+kind);this.kind=kind;}
    public Kind kind() {return kind;}
    public boolean tripsCircuit() {return kind!=Kind.CLIENT_CANCELLED && kind!=Kind.UPSTREAM_REJECTED;}
}
