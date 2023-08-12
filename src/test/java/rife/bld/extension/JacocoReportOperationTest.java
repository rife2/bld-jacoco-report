/*
 *  Copyright 2023 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package rife.bld.extension;

import org.junit.jupiter.api.Test;
import rife.bld.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class JacocoReportOperationTest {
    final File csv;
    final File html;
    final Path tempDir;
    final File xml;

    JacocoReportOperationTest() throws IOException {
        tempDir = Files.createTempDirectory("jacoco-test");
        csv = (new File(tempDir.toFile(), "jacoco.csv"));
        html = (new File(tempDir.toFile(), "html"));
        xml = (new File(tempDir.toFile(), "jacoco.xml"));
    }

    static void deleteOnExit(File folder) {
        folder.deleteOnExit();
        for (var f : Objects.requireNonNull(folder.listFiles())) {
            if (f.isDirectory()) {
                deleteOnExit(f);
            } else {
                f.deleteOnExit();
            }
        }
    }

    @Test
    void executeTest() throws IOException {
        newJacocoReportOperation().execute();

        assertThat(csv).exists();
        assertThat(html).isDirectory();
        assertThat(xml).exists();
        try (var lines = Files.lines(xml.toPath())) {
            assertThat(lines.anyMatch(s ->
                    s.contains("<counter type=\"INSTRUCTION\" missed=\"0\" covered=\"3\"/>"))).isTrue();
        }
        assertThat(Path.of(html.getPath(), "com.example", "Examples.java.html")).exists();
        deleteOnExit(tempDir.toFile());
    }

    JacocoReportOperation newJacocoReportOperation() {
        var o = new JacocoReportOperation();
        o.fromProject(new Project());
        o.csv(csv);
        o.html(html);
        o.xml(xml);
        o.classFiles(new File("src/test/resources/Examples.class"));
        o.sourceFiles(new File("examples/src/main/java"));
        o.execFiles(new File("src/test/resources/jacoco.exec"));
        return o;
    }
}