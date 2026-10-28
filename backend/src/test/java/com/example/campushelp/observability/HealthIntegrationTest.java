package com.example.campushelp.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class HealthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Test void publicProbesReportOnlyStatusWithoutDetailsOrConfiguration() throws Exception {
        for(String path:new String[]{"/actuator/health/liveness","/actuator/health/readiness"}) {
            var r=mvc.perform(get(path)).andReturn().getResponse();
            assertEquals(200,r.getStatus());var value=json.readTree(r.getContentAsByteArray());
            assertEquals("UP",value.path("status").asText());assertEquals(1,value.size());
        }
    }
    @Test void operationalInternalsAreNotPubliclyExposed() throws Exception {
        for(String path:new String[]{"/actuator/env","/actuator/configprops","/actuator/flyway","/actuator/shutdown","/api/ops/model-observations"})
            assertEquals(403,mvc.perform(get(path)).andReturn().getResponse().getStatus());
    }
}
