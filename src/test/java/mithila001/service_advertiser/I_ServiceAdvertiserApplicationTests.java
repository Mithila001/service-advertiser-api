package mithila001.service_advertiser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@AutoConfigureMockMvc
class I_ServiceAdvertiserApplicationTests extends PostgresTestSupport {

    @Autowired
    MockMvc mvc;

	@Test
	void contextLoads() {
	}

    @Test
    void healthIsPublicStatusOnlyAndOtherActuatorPathsRemainProtected() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(header().exists("X-Request-ID"));
        mvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

}
