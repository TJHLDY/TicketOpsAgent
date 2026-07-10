package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformBaselineContractTest {

    @Test
    void pomUsesSpringAi2PlatformBaseline() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom)
                .contains("<version>4.1.0</version>")
                .contains("<java.version>21</java.version>")
                .contains("<spring-ai.version>2.0.0</spring-ai.version>");
    }
}
