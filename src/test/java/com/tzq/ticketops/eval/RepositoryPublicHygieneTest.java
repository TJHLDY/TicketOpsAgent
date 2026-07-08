package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryPublicHygieneTest {

    @Test
    void publicDocsDoNotContainLocalAbsolutePaths() throws Exception {
        List<Path> publicDocs = new ArrayList<>();
        publicDocs.add(Path.of("README.md"));

        try (Stream<Path> docs = Files.walk(Path.of("docs"))) {
            docs.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .forEach(publicDocs::add);
        }

        List<String> offendingFiles = new ArrayList<>();
        for (Path path : publicDocs) {
            String content = Files.readString(path);
            if (content.contains("C:\\Users") || content.contains("D:\\Projects")) {
                offendingFiles.add(path.toString());
            }
        }

        assertThat(offendingFiles).isEmpty();
    }
}
