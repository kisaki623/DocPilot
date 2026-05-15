package com.docpilot.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.agent.selector.llm-provider=disabled",
        "app.agent.selector.llm-model=",
        "app.agent.selector.llm-base-url=",
        "app.agent.selector.llm-api-key=",
        "app.agent.selector.shadow-enabled=false",
        "app.agent.selector.real-shadow-enabled=false",
        "app.agent.selector.real-shadow-record-metrics=false"
})
class DocPilotApplicationTests {

    @Test
    void contextLoads() {
    }
}

