package org.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AgentTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Test
    void testWeatherAgent() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(post("/api/agent/process")
                        .contentType("application/json")
                        .content("{\"task\": \"北京今天天气怎么样？\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("北京")));
    }

    @Test
    void testResearchAgent() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(post("/api/agent/process")
                        .contentType("application/json")
                        .content("{\"task\": \"请分析一下人工智能的发展趋势\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void testWritingAgent() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(post("/api/agent/process")
                        .contentType("application/json")
                        .content("{\"task\": \"请写一篇关于春天的短文\"}"))
                .andExpect(status().isOk());
    }
}
