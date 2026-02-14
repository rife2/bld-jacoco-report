/*
 * Copyright 2023-2026 the original author or authors.
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import rife.bld.Project;
import rife.bld.blueprints.BaseProjectBlueprint;
import rife.bld.extension.testing.LoggingExtension;
import rife.bld.extension.testing.TestLogHandler;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(LoggingExtension.class)
@SuppressWarnings({"PMD.AvoidDuplicateLiterals"})
class JacocoReportOperationTest {

    @SuppressWarnings("LoggerInitializedWithForeignClass")
    private static final Logger LOGGER = Logger.getLogger(JacocoReportOperation.class.getName());
    private static final TestLogHandler TEST_LOG_HANDLER = new TestLogHandler();
    @RegisterExtension
    @SuppressWarnings("unused")
    private static final LoggingExtension LOGGING_EXTENSION = new LoggingExtension(
            LOGGER,
            TEST_LOG_HANDLER,
            Level.ALL
    );
    private File csvFile;
    private File htmlDir;
    @TempDir
    private File tempDir;
    private File xmlFile;

    @BeforeEach
    void beforeEach() {
        csvFile = (new File(tempDir, "jacoco.csv"));
        htmlDir = (new File(tempDir, "html"));
        xmlFile = (new File(tempDir, "jacoco.xml"));
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

    @Test
    void fluentConfiguration() {
        var csvFile = new File("report.csv");
        var htmlDir = new File("report/html");
        var xmlFile = new File("report.xml");
        var destFile = new File("jacoco.exec");
        var sourceFile = new File("src/main/java");
        var classFile = new File("build/classes");
        var execFile = new File("build/jacoco.exec");

        var op = new JacocoReportOperation()
                .csv(csvFile)
                .html(htmlDir)
                .xml(xmlFile)
                .destFile(destFile)
                .encoding("UTF-16")
                .name("My Custom Report")
                .tabWidth(2)
                .quiet(true)
                .sourceFiles(sourceFile)
                .classFiles(classFile)
                .execFiles(execFile)
                .testToolOptions("-v", "--fail-fast");

        assertThat(op.csv()).isEqualTo(csvFile);
        assertThat(op.html()).isEqualTo(htmlDir);
        assertThat(op.xml()).isEqualTo(xmlFile);
        assertThat(op.destFile()).isEqualTo(destFile);
        assertThat(op.encoding()).isEqualTo("UTF-16");
        assertThat(op.name()).isEqualTo("My Custom Report");
        assertThat(op.tabWidth()).isEqualTo(2);
        assertThat(op.isQuiet()).isTrue();
        assertThat(op.sourceFiles()).containsExactly(sourceFile);
        assertThat(op.classFiles()).containsExactly(classFile);
        assertThat(op.execFiles()).containsExactly(execFile);
        assertThat(op.testToolOptions()).containsExactly("-v", "--fail-fast");
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

        @Test
        void executeFailureWhenProjectNotSet() {
            var op = new JacocoReportOperation();
            assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);
        }

        @Test
        void executeFailureWhenProjectNotSetAndLoggingDisabled() {
            TEST_LOG_HANDLER.setLevel(Level.OFF);
            var op = new JacocoReportOperation();
            assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);
            assertThat(TEST_LOG_HANDLER.getLogMessages()).isEmpty();
        }

        @Test
        void executeFailureWhenProjectNotSetAndSilent() {
            var op = new JacocoReportOperation().silent(true);
            assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);
            assertThat(TEST_LOG_HANDLER.getLogMessages()).isEmpty();
        }

        @Test
        void executeWithDestFile() throws Exception {
            var destFile = new File("examples/build/jacoco/jacoco.exec");
            var op = newJacocoReportOperation().destFile(destFile);
            op.execute();
            assertThat(destFile).exists();
        }

        @Test
        void executeWithEmptyClassFiles() {
            var op = newJacocoReportOperation();
            op.classFiles().clear();
            assertThatCode(op::execute).doesNotThrowAnyException();
        }

        @Test
        void executeWithEmptyExecFilesAndTestToolOptions() {
            var project = new BaseProjectBlueprint(new File("examples"), "com.example",
                    "examples", "Examples");
            var op = new JacocoReportOperation()
                    .fromProject(project)
                    .testToolOptions("--details=summary");
            op.execFiles().clear();
            assertThatCode(op::execute).doesNotThrowAnyException();
        }

        @Test
        void executeWithEmptyExecFilesWithLoggingDisabled() {
            TEST_LOG_HANDLER.setLevel(Level.OFF);
            var project = new BaseProjectBlueprint(new File("examples"), "com.example",
                    "examples", "Examples");
            var op = new JacocoReportOperation().fromProject(project);
            op.execFiles().clear();
            assertThatCode(op::execute).doesNotThrowAnyException();
            assertThat(TEST_LOG_HANDLER.getLogMessages()).isEmpty();
        }

        @Test
        void executeWithEmptyExecFilesWithSilent() {
            var project = new BaseProjectBlueprint(new File("examples"), "com.example",
                    "examples", "Examples");
            var op = new JacocoReportOperation()
                    .silent(true)
                    .fromProject(project);
            op.execFiles().clear();
            assertThatCode(op::execute).doesNotThrowAnyException();
            assertThat(TEST_LOG_HANDLER.getLogMessages()).isEmpty();
        }

        @Test
        void executeWithEmptySourceFiles() {
            var op = newJacocoReportOperation();
            op.sourceFiles().clear();
            assertThatCode(op::execute).doesNotThrowAnyException();
        }

        @Test
        void executeWithLoggingDisabled() throws Exception {
            TEST_LOG_HANDLER.setLevel(Level.OFF);
            newJacocoReportOperation().execute();
            assertThat(TEST_LOG_HANDLER.getLogMessages()).isEmpty();
        }

        @Test
        void executeWithNullCsvFile() throws Exception {
            var op = newJacocoReportOperation().csv((File) null);
            op.execute();
            assertThat(new File("examples/build/reports/jacoco/test/jacocoTestReport.csv")).exists();
        }

        @Test
        void executeWithNullHtmlFile() throws Exception {
            var op = newJacocoReportOperation().html((File) null);
            op.execute();
            assertThat(new File("examples/build/reports/jacoco/test/html/index.html")).exists();
        }

        @Test
        void executeWithNullXmlFile() throws Exception {
            var op = newJacocoReportOperation().xml((File) null);
            op.execute();
            assertThat(new File("examples/build/reports/jacoco/test/jacocoTestReport.xml")).exists();
        }

        @Test
        void executeWithQuiet() throws Exception {
            newJacocoReportOperation().quiet(true).execute();
            assertThat(TEST_LOG_HANDLER.getLogMessages()).isEmpty();
        }

        @Test
        void executeWithSilent() throws Exception {
            newJacocoReportOperation().silent(true).execute();
            assertThat(TEST_LOG_HANDLER.getLogMessages()).isEmpty();
        }

        @Test
        void executeWithTestOperation() throws Exception {
            newJacocoReportOperation().testOperation(new Project().testOperation()).execute();
            try (var softly = new AutoCloseableSoftAssertions()) {
                try (var lines = Files.lines(xmlFile.toPath())) {
                    softly.assertThat(lines.anyMatch(s ->
                            s.contains("<counter type=\"INSTRUCTION\" missed=\"0\" covered=\"3\"/>"))).isTrue();
                }
            }
        }

        JacocoReportOperation newJacocoReportOperation() {
            return new JacocoReportOperation()
                    .fromProject(new Project())
                    .csv(csvFile)
                    .html(htmlDir)
                    .xml(xmlFile)
                    .classFiles(new File("src/test/resources/Examples.class"))
                    .sourceFiles(new File("examples/src/main/java"))
                    .execFiles(new File("src/test/resources/jacoco.exec"));
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
        @DisplayName("Dest File Tests")
        class DestFileTests {

            @Test
            void destFileAsFile() {
                op.destFile(fooFile);
                assertThat(op.destFile()).isEqualTo(fooFile);
            }

            @Test
            void destFileAsPath() {
                var fooPath = fooFile.toPath();
                op.destFile(fooPath);
                assertThat(op.destFile()).isEqualTo(fooFile);
            }

            @Test
            void destFileAsString() {
                op.destFile("foo");
                assertThat(op.destFile()).isEqualTo(fooFile);
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

    @Nested
    @DisplayName("Options Tests")
    class OptionsTests {

        static final String FOO = "foo";
        private final File fooFile = new File(FOO);

        @Test
        void encoding() {
            var op = new JacocoReportOperation();
            op.encoding("UTF-8");
            assertThat(op.encoding()).isEqualTo("UTF-8");
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

        @Nested
        @DisplayName("CSV Tests")
        class CsvTests {

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
        }

        @Nested
        @DisplayName("HTML Tests")
        class HtmlTests {

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
        }

        @Nested
        @DisplayName("Quiet Tests")
        class QuietTests {

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
        }

        @Nested
        @DisplayName("Test Tool Options Tests")
        class TestToolOptionsTests {

            static final String BAR = "bar";

            @Test
            void testToolOptionsAsArray() {
                var op = new JacocoReportOperation();
                op = op.testToolOptions(FOO, BAR);
                assertThat(op.testToolOptions()).contains(FOO, BAR);
            }

            @Test
            void testToolOptionsAsList() {
                var op = new JacocoReportOperation();
                op.testToolOptions(List.of(FOO, BAR));
                assertThat(op.testToolOptions()).contains(FOO, BAR);
            }
        }

        @Nested
        @DisplayName("XML Tests")
        class XmlTests {

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
    }
}
