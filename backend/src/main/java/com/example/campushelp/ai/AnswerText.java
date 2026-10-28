package com.example.campushelp.ai;
import java.util.Locale;
import java.util.function.Consumer;
/** Finite explicit reasoning-envelope filter, applied before SSE and persistence. */
final class AnswerText {
    private static final String[] TAGS={"think","analysis","reasoning"};
    private final StringBuilder pending=new StringBuilder();
    private String hidden="";
    void append(String text,Consumer<String> visible) {
        var out=new StringBuilder();
        for(int i=0;i<text.length();i++) {
            pending.append(text.charAt(i));
            while(!pending.isEmpty()) {
                String lower=pending.toString().toLowerCase(Locale.ROOT);
                if(hidden.isEmpty()) {
                    String open=opening(lower);
                    if(open!=null) {hidden=open;pending.setLength(0);break;}
                    if(prefix(lower)) break;
                    out.append(pending.charAt(0));pending.deleteCharAt(0);
                } else {
                    if(lower.equals("</"+hidden+">")) {hidden="";pending.setLength(0);break;}
                    if(("</"+hidden+">").startsWith(lower)) break;
                    pending.deleteCharAt(0);
                }
            }
        }
        if(!out.isEmpty()) visible.accept(out.toString());
    }
    void finish(Consumer<String> visible) {
        if(hidden.isEmpty() && pending.toString().equals("<")) visible.accept("<");
        pending.setLength(0);
    }
    private String opening(String value) {for(String tag:TAGS) if(value.equals("<"+tag+">")) return tag;return null;}
    private boolean prefix(String value) {for(String tag:TAGS) if(("<"+tag+">").startsWith(value)) return true;return false;}
}
