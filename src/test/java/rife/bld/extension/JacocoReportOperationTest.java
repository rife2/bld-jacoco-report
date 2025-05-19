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
    final File csvFile;
    final File htmlDir;
    final Path tempDir;
    final File xmlFile;

    JacocoReportOperationTest() throws IOException {
        tempDir = Files.createTempDirectory("jacoco-test");
        csvFile = (new File(tempDir.toFile(), "jacoco.csv"));
        htmlDir = (new File(tempDir.toFile(), "html"));
        xmlFile = (new File(tempDir.toFile(), "jacoco.xml"));
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
        @Test
        void execute() throws Exception {
            newJacocoReportOperation().execute();

            assertThat(csvFile).exists();
            assertThat(htmlDir).isDirectory();
            assertThat(xmlFile).exists();
            try (var softly = new AutoCloseableSoftAssertions()) {
                try (var lines = Files.lines(xmlFile.toPath())) {
                    softly.assertThat(lines.anyMatch(s ->
                            s.contains("<counter type=\"INSTRUCTION\" missed=\"0\" covered=\"3\"/>"))).isTrue();
                }
            }
            assertThat(Path.of(htmlDir.getPath(), "com.example", "Examples.java.html")).exists();
        }

        @Test
        void executeFailure() {
            var op = new JacocoReportOperation().fromProject(new Project());
            assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);
        }

        JacocoReportOperation newJacocoReportOperation() {
            var op = new JacocoReportOperation()
                    .fromProject(new Project())
                    .csv(csvFile)
                    .html(htmlDir)
                    .xml(xmlFile)
                    .classFiles(new File("src/test/resources/Examples.class"))
                    .sourceFiles(new File("examples/src/main/java"))
                    .execFiles(new File("src/test/resources/jacoco.exec"));

            deleteOnExit(tempDir.toFile());

            return op;
        }
    }


    @DisplayName("Options Tests")
    class OptionsTests {
        public static final String FOO = "foo";
        private final File fooFile = new File(FOO);

        @Test
        void csvAsFile() {
            var op = new JacocoReportOperation();
            op.csv(fooFile);
            assertThat(op.csv()).isEqualTo(fooFile);
        }

        @Test
        void csvAsPath() {
            var fooPath = fooFile.toPath();
            var op = new JacocoReportOperation();
            op.csv(fooPath);
            assertThat(op.csv()).isEqualTo(fooFile);
        }

        @Test
        void csvAsString() {
            var op = new JacocoReportOperation();
            op.csv(FOO);
            assertThat(op.csv()).isEqualTo(fooFile);
        }

        @Test
        void htmlAsFile() {
            var op = new JacocoReportOperation();
            op.html(fooFile);
            assertThat(op.html()).isEqualTo(fooFile);
        }

        @Test
        void htmlAsPath() {
            var fooPath = fooFile.toPath();
            var op = new JacocoReportOperation();
            op.html(fooPath);
            assertThat(op.html()).isEqualTo(fooFile);
        }

        @Test
        void htmlAsString() {
            var op = new JacocoReportOperation();
            op.html(FOO);
            assertThat(op.html()).isEqualTo(fooFile);
        }

        @Test
        void quietIsFalse() {
            var op = new JacocoReportOperation();
            op.quiet(false);
            assertThat(op.isQuiet()).isFalse();
        }

        @Test
        void quietIsTrue() {
            var op = new JacocoReportOperation();
            op.quiet(true);
            assertThat(op.isQuiet()).isTrue();
        }

        @Test
        void reportName() {
            var op = new JacocoReportOperation();
            op.name(FOO);
            assertThat(op.name()).isEqualTo(FOO);
        }

        @Test
        void tabWidth() {
            var op = new JacocoReportOperation();
            op.tabWidth(4);
            assertThat(op.tabWidth()).isEqualTo(4);
        }

        @Test
        void xmlAsFile() {
            var op = new JacocoReportOperation();
            op.xml(fooFile);
            assertThat(op.xml()).isEqualTo(fooFile);
        }

        @Test
        void xmlAsPath() {
            var fooPath = fooFile.toPath();
            var op = new JacocoReportOperation();
            op.xml(fooPath);
            assertThat(op.xml()).isEqualTo(fooFile);
        }

        @Test
        void xmlAsString() {
            var op = new JacocoReportOperation();
            op.xml(FOO);
            assertThat(op.xml()).isEqualTo(fooFile);
        }
    }

    @Nested
    @DisplayName("File Operation Tests")
    class FilesOperationTests {
        private final File barFile = new File("bar");
        private final File fooFile = new File("foo");
        private final JacocoReportOperation op = new JacocoReportOperation();

        @Nested
        @DisplayName("Class Files Tests")
        class ClassFilesTests {
            @Test
            void classFilesAsFileArray() {
                op.classFiles().clear();
                assertThat(op.classFiles()).isEmpty();
                op.classFiles(fooFile, barFile);
                assertThat(op.classFiles()).contains(fooFile, barFile);
            }

            @Test
            void classFilesAsFileList() {
                op.classFiles().clear();
                assertThat(op.classFiles()).isEmpty();
                op.classFiles(List.of(fooFile, barFile));
                assertThat(op.classFiles()).contains(fooFile, barFile);
            }

            @Test
            void classFilesAsPathArray() {
                op.classFiles().clear();
                assertThat(op.classFiles()).isEmpty();
                op.classFiles(fooFile.toPath(), barFile.toPath());
                assertThat(op.classFiles()).contains(fooFile, barFile);
            }

            @Test
            void classFilesAsPathList() {
                op.classFiles().clear();
                assertThat(op.classFiles()).isEmpty();
                op.classFilesPaths(List.of(fooFile.toPath(), barFile.toPath()));
                assertThat(op.classFiles()).contains(fooFile, barFile);
            }

            @Test
            void classFilesAsStringArray() {
                op.classFiles().clear();
                assertThat(op.classFiles()).isEmpty();
                op.classFiles("foo", "bar");
                assertThat(op.classFiles()).contains(fooFile, barFile);
            }

            @Test
            void classFilesAsStringList() {
                op.classFiles().clear();
                assertThat(op.classFiles()).isEmpty();
                op.classFilesStrings(List.of("foo", "bar"));
                assertThat(op.classFiles()).contains(fooFile, barFile);
            }
        }

        @Nested
        @DisplayName("Exec Files Tests")
        class ExecFilesTests {
            private final File barFile = new File("bar");
            private final File fooFile = new File("foo");
            private final JacocoReportOperation op = new JacocoReportOperation();

            @Test
            void execFilesAsFileArray() {
                op.execFiles().clear();
                assertThat(op.execFiles()).isEmpty();
                op.execFiles(fooFile, barFile);
                assertThat(op.execFiles()).contains(fooFile, barFile);
            }

            @Test
            void execFilesAsFileList() {
                op.execFiles().clear();
                assertThat(op.execFiles()).isEmpty();
                op.execFiles(List.of(fooFile, barFile));
                assertThat(op.execFiles()).contains(fooFile, barFile);
            }

            @Test
            void execFilesAsPathArray() {
                op.execFiles().clear();
                assertThat(op.execFiles()).isEmpty();
                op.execFiles(fooFile.toPath(), barFile.toPath());
                assertThat(op.execFiles()).contains(fooFile, barFile);
            }

            @Test
            void execFilesAsPathList() {
                op.execFiles().clear();
                assertThat(op.execFiles()).isEmpty();
                op.execFilesPaths(List.of(fooFile.toPath(), barFile.toPath()));
                assertThat(op.execFiles()).contains(fooFile, barFile);
                op.execFiles().clear();
            }

            @Test
            void execFilesAsStringArray() {
                op.execFiles().clear();
                assertThat(op.execFiles()).isEmpty();
                op.execFiles("foo", "bar");
                assertThat(op.execFiles()).contains(fooFile, barFile);
            }

            @Test
            void execFilesAsStringList() {
                op.execFiles().clear();
                assertThat(op.execFiles()).isEmpty();
                op.execFilesStrings(List.of("foo", "bar"));
                assertThat(op.execFiles()).contains(fooFile, barFile);
            }
        }

        @Nested
        @DisplayName("Source Files Tests")
        class SourceFilesTests {
            private final File barFile = new File("bar");
            private final File fooFile = new File("foo");
            private final JacocoReportOperation op = new JacocoReportOperation();

            @Test
            void sourceFilesAsFileArray() {
                op.sourceFiles().clear();
                assertThat(op.sourceFiles()).isEmpty();
                op.sourceFiles(fooFile, barFile);
                assertThat(op.sourceFiles()).as("File...").contains(fooFile, barFile);
            }

            @Test
            void sourceFilesAsFileList() {
                op.sourceFiles().clear();
                assertThat(op.sourceFiles()).isEmpty();
                op.sourceFiles(List.of(fooFile, barFile));
                assertThat(op.sourceFiles()).as("File...").contains(fooFile, barFile);
            }

            @Test
            void sourceFilesAsPathList() {
                op.sourceFiles().clear();
                assertThat(op.sourceFiles()).isEmpty();
                op.sourceFilesPaths(List.of(fooFile.toPath(), barFile.toPath()));
                assertThat(op.sourceFiles()).as("Path...").contains(fooFile, barFile);
            }

            @Test
            void sourceFilesAsStringArray() {
                op.sourceFiles().clear();
                assertThat(op.sourceFiles()).isEmpty();
                op.sourceFiles("foo", "bar");
                assertThat(op.sourceFiles()).as("String...").contains(fooFile, barFile);
            }

            @Test
            void sourceFilesAsStringList() {
                op.sourceFiles().clear();
                assertThat(op.sourceFiles()).isEmpty();
                op.sourceFilesStrings(List.of("foo", "bar"));
                assertThat(op.sourceFiles()).as("List(String...)").contains(fooFile, barFile);
            }

            @Test
            void sourceFilesPathArray() {
                op.sourceFiles().clear();
                assertThat(op.sourceFiles()).isEmpty();
                op.sourceFiles(fooFile.toPath(), barFile.toPath());
                assertThat(op.sourceFiles()).as("Path...").contains(fooFile, barFile);
            }
        }
    }
}
