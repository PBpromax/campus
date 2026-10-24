package com.example.campushelp.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.campushelp.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PromptContextTest {
    PromptContext context=new PromptContext(new ObjectMapper());
    @Test void refusesKnownSensitivePatternsWithoutEchoingThem() {
        for(String text:List.of("取件码123456","手机号13800138000","密码:abc123","sk-abcdefghijklmnop123456","a.person@example.com")) {
            var failure=assertThrows(BusinessException.class,()->context.prepare(List.of(Map.of("content",text)),Map.of()));assertFalse(failure.getMessage().contains(text));
        }
    }
    @Test void mathAndLocationsRemainUsableAndOnlyOldMessagesAreTrimmed() throws Exception {
        var messages=new ArrayList<Map<String,Object>>();messages.add(Map.of("role","system","content","规则"));
        for(int i=0;i<20;i++) messages.add(Map.of("role","user","content","旧上下文".repeat(1000)));
        messages.add(Map.of("role","user","content","图书馆东门；y=-x的导数为什么是-1"));
        var result=context.prepare(messages,Map.of());assertTrue(result.size()<messages.size());assertEquals(messages.getFirst(),result.getFirst());assertEquals(messages.getLast(),result.getLast());assertEquals(22,messages.size());
    }
    @Test void neverTruncatesAnOversizedCurrentRequest() {
        assertThrows(BusinessException.class,()->context.prepare(List.of(Map.of("content","system"),Map.of("content","大".repeat(20000))),Map.of()));
    }
}
