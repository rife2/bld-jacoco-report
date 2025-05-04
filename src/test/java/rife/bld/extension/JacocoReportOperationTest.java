/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rife.bld.extension;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import rife.bld.Project;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
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
    @EnabledOnOs(OS.LINUX)
    void checkAllParams() throws IOException {
        var supported = List.of("<execfiles>",
                "--classfiles",
                "--csv",
                "--encoding",
                "--html",
                "--name",
                "--quiet",
                "--sourcefiles",
                "--tabwith",
                "--xml");
        var args = Files.readAllLines(Paths.get("src", "test", "resources", "jacoco-args.txt"));

        assertThat(args).isNotEmpty();
        assertThat(supported).containsAll(args);

    }

    @Nested
    @DisplayName("Execute Tests")
    class ExecuteTests {
        JacocoReportOperation newJacocoReportOperation() {
            var op = new JacocoReportOperation()
                    .fromProject(new Project())
                    .csv(csv)
                    .html(html)
                    .xml(xml)
                    .classFiles(new File("src/test/resources/Examples.class"))
                    .sourceFiles(new File("examples/src/main/java"))
                    .execFiles(new File("src/test/resources/jacoco.exec"));

            deleteOnExit(tempDir.toFile());

            return op;
        }

        @Test
        void execute() throws Exception {
            newJacocoReportOperation().execute();

            assertThat(csv).exists();
            assertThat(html).isDirectory();
            assertThat(xml).exists();
            try (var softly = new AutoCloseableSoftAssertions()) {
                try (var lines = Files.lines(xml.toPath())) {
                    softly.assertThat(lines.anyMatch(s ->
                            s.contains("<counter type=\"INSTRUCTION\" missed=\"0\" covered=\"3\"/>"))).isTrue();
                }
            }
            assertThat(Path.of(html.getPath(), "com.example", "Examples.java.html")).exists();
        }

        @Test
        void executeFailure() {
            var op = new JacocoReportOperation().fromProject(new Project());
            assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);
        }
    }

    @Nested
    @DisplayName("File Operation Tests")
    class FilesOperationTests {
        private final File bar = new File("bar");
        private final File foo = new File("foo");
        private final JacocoReportOperation op = new JacocoReportOperation();

        @Nested
        @DisplayName("Class Files Tests")
        class ClassFilesTests {
            @Test
            void classFilesAsFileArray() {
                op.classFiles().clear();
                op.classFiles(foo, bar);
                assertThat(op.classFiles()).contains(foo, bar);
            }

            @Test
            void classFilesAsFileList() {
                op.classFiles().clear();
                op.classFiles(List.of(foo, bar));
                assertThat(op.classFiles()).contains(foo, bar);
            }

            @Test
            void classFilesAsPathArray() {
                op.classFiles().clear();
                op.classFiles(foo.toPath(), bar.toPath());
                assertThat(op.classFiles()).contains(foo, bar);
            }

            @Test
            void classFilesAsPathList() {
                op.classFiles().clear();
                op.classFilesPaths(List.of(foo.toPath(), bar.toPath()));
                assertThat(op.classFiles()).contains(foo, bar);
            }

            @Test
            void classFilesAsStringArray() {
                op.classFiles().clear();
                op.classFiles("foo", "bar");
                assertThat(op.classFiles()).contains(foo, bar);
            }

            @Test
            void classFilesAsStringList() {
                op.classFiles().clear();
                op.classFilesStrings(List.of("foo", "bar"));
                assertThat(op.classFiles()).contains(foo, bar);
            }
        }

        @Nested
        @DisplayName("Exec Files Tests")
        class ExecFilesTests {
            private final File bar = new File("bar");
            private final File foo = new File("foo");
            private final JacocoReportOperation op = new JacocoReportOperation();

            @Test
            void execFilesAsFileArray() {
                op.execFiles().clear();
                op.execFiles(foo, bar);
                assertThat(op.execFiles()).contains(foo, bar);
            }

            @Test
            void execFilesAsFileList() {
                op.execFiles().clear();
                op.execFiles(List.of(foo, bar));
                assertThat(op.execFiles()).contains(foo, bar);
            }

            @Test
            void execFilesAsPathArray() {
                op.execFiles().clear();
                op.execFiles(foo.toPath(), bar.toPath());
                assertThat(op.execFiles()).contains(foo, bar);
            }

            @Test
            void execFilesAsPathList() {
                op.execFiles().clear();
                op.execFilesPaths(List.of(foo.toPath(), bar.toPath()));
                assertThat(op.execFiles()).contains(foo, bar);
                op.execFiles().clear();
            }

            @Test
            void execFilesAsStringArray() {
                op.execFiles().clear();
                op.execFiles("foo", "bar");
                assertThat(op.execFiles()).contains(foo, bar);
            }

            @Test
            void execFilesAsStringList() {
                op.execFiles().clear();
                op.execFilesStrings(List.of("foo", "bar"));
                assertThat(op.execFiles()).contains(foo, bar);
            }
        }

        @Nested
        @DisplayName("Source Files Tests")
        class SourceFilesTests {
            private final File bar = new File("bar");
            private final File foo = new File("foo");
            private final JacocoReportOperation op = new JacocoReportOperation();

            @Test
            void sourceFilesAsFileArray() {
                op.sourceFiles().clear();
                op.sourceFiles(foo, bar);
                assertThat(op.sourceFiles()).as("File...").contains(foo, bar);
            }

            @Test
            void sourceFilesAsFileList() {
                op.sourceFiles().clear();
                op.sourceFiles(List.of(foo, bar));
                assertThat(op.sourceFiles()).as("File...").contains(foo, bar);
            }

            @Test
            void sourceFilesAsPathList() {
                op.sourceFiles().clear();
                op.sourceFilesPaths(List.of(foo.toPath(), bar.toPath()));
                assertThat(op.sourceFiles()).as("Path...").contains(foo, bar);
            }

            @Test
            void sourceFilesAsStringArray() {
                op.sourceFiles().clear();
                op.sourceFiles("foo", "bar");
                assertThat(op.sourceFiles()).as("String...").contains(foo, bar);
            }

            @Test
            void sourceFilesAsStringList() {
                op.sourceFiles().clear();
                op.sourceFilesStrings(List.of("foo", "bar"));
                assertThat(op.sourceFiles()).as("List(String...)").contains(foo, bar);
            }

            @Test
            void sourceFilesPathArray() {
                op.sourceFiles().clear();
                op.sourceFiles(foo.toPath(), bar.toPath());
                assertThat(op.sourceFiles()).as("Path...").contains(foo, bar);
            }
        }
    }
}
