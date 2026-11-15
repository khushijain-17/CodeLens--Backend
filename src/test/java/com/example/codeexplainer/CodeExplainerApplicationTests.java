package com.example.codeexplainer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Needs a real database and Groq key (env vars); run manually with full config")
class CodeExplainerApplicationTests {

    @Test
    void contextLoads() {
    }
}