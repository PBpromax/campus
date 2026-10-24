package com.example.campushelp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ToolActionHintsTest {
    final ToolActionHints hints=new ToolActionHints(new ObjectMapper());
    @Test void explicitPublishRemainsSuggestion() throws Exception {
        var hint=hints.validate("{\"action\":\"PUBLISH\",\"demandId\":null}","请发布，先核对后执行");
        assertEquals("PUBLISH",hint.action()); assertNull(hint.demandId());
    }
    @Test void explicitTargetAcceptAndCancel() throws Exception {
        assertEquals(42L,hints.validate("{\"action\":\"ACCEPT\",\"demandId\":42}","我要接单42").demandId());
        assertEquals(42L,hints.validate("{\"action\":\"CANCEL\",\"demandId\":42}","取消任务42").demandId());
    }
    @ParameterizedTest @ValueSource(strings={"不要发布","不需要发布","先别发","我只问一道题"})
    void noExplicitAffirmativeIntent(String text) { assertThrows(Exception.class,()->hints.validate("{\"action\":\"PUBLISH\",\"demandId\":null}",text)); }
    @ParameterizedTest @ValueSource(strings={
      "{\"action\":\"EXECUTE_SQL\",\"demandId\":42}",
      "{\"action\":\"ACCEPT\",\"demandId\":4}",
      "{\"action\":\"PUBLISH\",\"demandId\":42}",
      "{\"action\":\"ACCEPT\",\"demandId\":42,\"confirmed\":true}",
      "{\"action\":\"ACCEPT\",\"demandId\":42.1}"})
    void rejectsInventedOrExtraArguments(String args) { assertThrows(Exception.class,()->hints.validate(args,"我要接单42并发布")); }
}
