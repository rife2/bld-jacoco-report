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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(LoggingExtension.class)
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.ExcessiveImports"})
class JacocoReportOperationTest {

    @SuppressWarnings("LoggerInitializedWithForeignClass")
    private static final Logger logger = Logger.getLogger(JacocoReportOperation.class.getName());
    private static final TestLogHandler testLogHandler = new TestLogHandler();
    @RegisterExtension
    @SuppressWarnings("unused")
    private static final LoggingExtension loggingExtension = new LoggingExtension(
            logger,
            testLogHandler,
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

        testLogHandler.clear();
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
            testLogHandler.printLogMessages();

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
        void executeCalledTwiceProducesSameReports() {
            var op = newJacocoReportOperation();
            assertThatCode(op::execute).doesNotThrowAnyException();
            testLogHandler.printLogMessages();

            assertThat(csvFile).exists();
            assertThat(xmlFile).exists();
            assertThat(htmlDir).isDirectory();
        }

        @Test
        void executeFailure() {
            var op = new JacocoReportOperation().fromProject(new Project());

            assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);
        }

        @Test
        void executeFailureWhenProjectNotSet() {
            var op = new JacocoReportOperation();
            assertThatCode(op::execute).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("project");
        }

        @Test
        void executeFailureWhenProjectNotSetAndLoggingDisabled() {
            testLogHandler.setLevel(Level.OFF);
            var op = new JacocoReportOperation();
            assertThatCode(op::execute).isInstanceOf(NullPointerException.class);
            assertThat(testLogHandler.getLogMessages()).isEmpty();
        }

        @Test
        void executeFailureWhenProjectNotSetAndSilent() {
            var op = new JacocoReportOperation().silent(true);
            assertThatCode(op::execute).isInstanceOf(NullPointerException.class);
            assertThat(testLogHandler.getLogMessages()).isEmpty();
        }

        @Test
        void executeIsIdempotentForClassFiles() throws Exception {
            var op = newJacocoReportOperation();
            var before = List.copyOf(op.classFiles());
            op.execute();
            testLogHandler.printLogMessages();
            assertThat(op.classFiles()).isEqualTo(before);
        }

        @Test
        void executeIsIdempotentForExecFiles() throws Exception {
            var op = newJacocoReportOperation();
            var before = List.copyOf(op.execFiles());
            op.execute();
            testLogHandler.printLogMessages();
            assertThat(op.execFiles()).isEqualTo(before);
        }

        @Test
        void executeIsIdempotentForSourceFiles() throws Exception {
            var op = newJacocoReportOperation();
            var before = List.copyOf(op.sourceFiles());
            op.execute();
            testLogHandler.printLogMessages();
            assertThat(op.sourceFiles()).isEqualTo(before);
        }

        @Test
        void executeWithDestFile() throws Exception {
            var destFile = new File("examples/build/jacoco/jacoco.exec");
            var op = newJacocoReportOperation().destFile(destFile);
            op.execute();
            testLogHandler.printLogMessages();
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
            testLogHandler.printLogMessages();
        }

        @Test
        void executeWithEmptyExecFilesWithLoggingDisabled() {
            testLogHandler.setLevel(Level.OFF);
            var project = new BaseProjectBlueprint(new File("examples"), "com.example",
                    "examples", "Examples");
            var op = new JacocoReportOperation().fromProject(project);
            op.execFiles().clear();
            assertThatCode(op::execute).doesNotThrowAnyException();
            assertThat(testLogHandler.getLogMessages()).isEmpty();
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
            assertThat(testLogHandler.getLogMessages()).isEmpty();
        }

        @Test
        void executeWithEmptySourceFiles() {
            var op = newJacocoReportOperation();
            op.sourceFiles().clear();
            assertThatCode(op::execute).doesNotThrowAnyException();
            testLogHandler.printLogMessages();
        }

        @Test
        void executeWithLoggingDisabled() throws Exception {
            testLogHandler.setLevel(Level.OFF);
            newJacocoReportOperation().execute();
            assertThat(testLogHandler.getLogMessages()).isEmpty();
        }

        @Test
        void executeWithMissingExecFileAndLoggingDisabled() {
            testLogHandler.setLevel(Level.OFF);
            var op = newJacocoReportOperation()
                    .execFiles(new File("does/not/exist.exec"));
            op.execFiles().removeIf(f -> "jacoco.exec".equals(f.getName()));
            assertThatCode(op::execute).doesNotThrowAnyException();
            assertThat(testLogHandler.getLogMessages()).isEmpty();
        }

        @Test
        void executeWithMissingExecFileAndSilent() {
            var op = newJacocoReportOperation()
                    .silent(true)
                    .execFiles(new File("does/not/exist.exec"));
            op.execFiles().removeIf(f -> "jacoco.exec".equals(f.getName()));
            assertThatCode(op::execute).doesNotThrowAnyException();
            assertThat(testLogHandler.getLogMessages()).isEmpty();
        }

        @Test
        void executeWithMissingExecFileLogsWarning() {
            var op = newJacocoReportOperation()
                    .execFiles(new File("does/not/exist.exec"));
            op.execFiles().removeIf(f -> "jacoco.exec".equals(f.getName()));
            assertThatCode(op::execute).doesNotThrowAnyException();
            testLogHandler.printLogMessages();
            assertThat(testLogHandler.getLogMessages())
                    .anyMatch(m -> m.contains("not found") || m.contains("skipping"));
        }

        @Test
        @SuppressWarnings("DataFlowIssue")
        void executeWithNullCsvFile() {
            var op = newJacocoReportOperation();
            assertThatThrownBy(() -> op.csv((File) null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @SuppressWarnings("DataFlowIssue")
        void executeWithNullHtmlFile() {
            var op = newJacocoReportOperation();
            assertThatThrownBy(() -> op.html((File) null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @SuppressWarnings("DataFlowIssue")
        void executeWithNullXmlFile() {
            var op = newJacocoReportOperation();
            assertThatThrownBy(() -> op.xml((File) null)).isInstanceOf(NullPointerException.class);

        }

        @Test
        void executeWithQuiet() throws Exception {
            newJacocoReportOperation().quiet(true).execute();
            assertThat(testLogHandler.getLogMessages()).isEmpty();
        }

        @Test
        void executeWithSilent() throws Exception {
            newJacocoReportOperation().silent(true).execute();
            assertThat(testLogHandler.getLogMessages()).isEmpty();
        }

        @Test
        void executeWithTestOperation() throws Exception {
            var op = newJacocoReportOperation()
                    .execFiles(new File("src/test/resources/jacoco.exec")) // explicit
                    .classFiles(new File("src/test/resources/Examples.class"));

            op.execute();
            testLogHandler.printLogMessages();

            assertThat(xmlFile).exists();
            var xmlContent = Files.readString(xmlFile.toPath());
            assertThat(xmlContent).contains("<counter type=\"INSTRUCTION\" missed=\"0\" covered=\"3\"/>");
        }

        JacocoReportOperation newJacocoReportOperation() {
            return new JacocoReportOperation()
                    .fromProject(new BaseProjectBlueprint(
                            new File("examples"),
                            "com.example",
                            "examples",
                            "Examples"))
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

        @Nested
        @DisplayName("Test Operation Tests")
        class TestOperationTests {

            @Test
            @SuppressWarnings("DataFlowIssue")
            void testOperationRejectsNull() {
                var op = new JacocoReportOperation();
                assertThatCode(() -> op.testOperation(null)).isInstanceOf(NullPointerException.class);
            }

            @Test
            void testOperationRoundTrip() {
                var op = new JacocoReportOperation();
                var testOp = new Project().testOperation();
                op.testOperation(testOp);
                // No getter exists; verify execute picks it up without NPE when project is set
                assertThat(op).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Include/Exclude Filter Tests")
    class IncludeExcludeTests {

        @Test
        void clearingViaGetterMutatesLiveList() {
            var op = new JacocoReportOperation()
                    .includes("foo", "bar");

            assertThat(op.includes()).hasSize(2);
            op.includes().clear();
            assertThat(op.includes()).isEmpty();
        }

        @Test
        @SuppressWarnings("DataFlowIssue")
        void excludesRejectsNullOrEmpty() {
            var op = new JacocoReportOperation();
            assertThatThrownBy(() -> op.excludes((String[]) null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> op.excludes(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("patterns must not be empty");
            assertThatThrownBy(() -> op.excludes((Collection<String>) null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> op.excludes(List.of("")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void excludesTakesPrecedenceOverIncludes() throws Exception {
            var op = newJacocoReportOperation()
                    .includes("com/example/**")
                    .excludes("**/Examples");

            op.execute();

            assertThat(xmlFile).exists();
            try (var lines = Files.lines(xmlFile.toPath())) {
                assertThat(lines.anyMatch(s -> s.contains("com/example/Examples")))
                        .as("Excluded class should NOT be in report")
                        .isFalse();
            }
            assertThat(testLogHandler.getLogMessages())
                    .anyMatch(m -> m.contains("No classes matched include/exclude patterns"));
        }

        @Test
        void excludesWithCollection() throws Exception {
            var op = newJacocoReportOperation()
                    .excludes(List.of("**/Examples")); // uses Collection overload

            op.execute();

            try (var lines = Files.lines(xmlFile.toPath())) {
                assertThat(lines.anyMatch(s -> s.contains("com/example/Examples")))
                        .as("Collection overload should work")
                        .isFalse();
            }
            assertThat(op.excludes()).containsExactly("**/Examples");
        }

        @Test
        void includesWithCollection() throws Exception {
            var op = newJacocoReportOperation()
                    .includes(List.of("com/example/**", "com/other/**")); // uses Collection overload

            op.execute();

            try (var lines = Files.lines(xmlFile.toPath())) {
                assertThat(lines.anyMatch(s -> s.contains("com/example/Examples")))
                        .as("Collection overload should work")
                        .isTrue();
            }
            assertThat(op.includes()).containsExactly("com/example/**", "com/other/**");
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

        @Test
        void noMatchLogsWarningWhenPatternsSet() throws Exception {
            var op = newJacocoReportOperation()
                    .includes("com/nonexistent/**");

            op.execute();

            testLogHandler.printLogMessages();

            assertThat(xmlFile).exists();
            var xmlContent = Files.readString(xmlFile.toPath());

            //System.out.println(xmlContent);

            assertThat(xmlContent)
                    .as("No classes should be reported")
                    .doesNotContain("<package")  // no packages
                    .doesNotContain("<class");   // no classes

            assertThat(testLogHandler.getLogMessages())
                    .anyMatch(m -> m.contains("No classes matched include/exclude patterns"))
                    .anyMatch(m -> m.contains("Use '/' separators"));
        }

        @Test
        void singleQuestionMarkWildcard() throws Exception {
            var op = newJacocoReportOperation()
                    .includes("com/example/Example?");

            op.execute();
            try (var lines = Files.lines(xmlFile.toPath())) {
                assertThat(lines.anyMatch(s -> s.contains("com/example/Examples")))
                        .as("? wildcard should match single char")
                        .isTrue();
            }
        }

        @Test
        void wildcardPatternsAcceptedByApi() {
            var op = new JacocoReportOperation();

            assertThatCode(() -> op.includes("com/myapp/**", "**/service/*", "com/myapp/MyClass"))
                    .doesNotThrowAnyException();
            assertThat(op.includes()).containsExactly("com/myapp/**", "**/service/*", "com/myapp/MyClass");

            assertThatCode(() -> op.excludes("**/*Test*", "**/generated/**", "**/Dagger*"))
                    .doesNotThrowAnyException();
            assertThat(op.excludes()).containsExactly("**/*Test*", "**/generated/**", "**/Dagger*");
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
        @DisplayName("Dest File Tests (Options)")
        class DestFileOptionTests {

            private final File fooFile = new File("foo");

            @Test
            void destFileAsFile() {
                var op = new JacocoReportOperation();
                op.destFile(fooFile);
                assertThat(op.destFile()).isEqualTo(fooFile);
            }

            @Test
            void destFileAsPath() {
                var op = new JacocoReportOperation();
                op.destFile(fooFile.toPath());
                assertThat(op.destFile()).isEqualTo(fooFile);
            }

            @Test
            void destFileAsString() {
                var op = new JacocoReportOperation();
                op.destFile("foo");
                assertThat(op.destFile()).isEqualTo(fooFile);
            }

            @Test
            void destFileIsNotMutatedAfterExecute() throws Exception {
                var op = new JacocoReportOperation()
                        .fromProject(new Project())
                        .csv(csvFile)
                        .html(htmlDir)
                        .xml(xmlFile)
                        .classFiles(new File("src/test/resources/Examples.class"))
                        .sourceFiles(new File("examples/src/main/java"))
                        .execFiles(new File("src/test/resources/jacoco.exec"));
                assertThat(op.destFile()).isNull();
                op.execute();
                assertThat(op.destFile()).isNull();
            }

            @Test
            void destFileIsNullByDefault() {
                var op = new JacocoReportOperation();
                assertThat(op.destFile()).isNull();
            }
        }

        @Nested
        @DisplayName("Encoding Tests")
        class EncodingTests {

            @Test
            void encodingIsNullByDefault() {
                var op = new JacocoReportOperation();
                assertThat(op.encoding()).isNull();
            }

            @Test
            void encodingRoundTrip() {
                var op = new JacocoReportOperation();
                op.encoding("UTF-8");
                assertThat(op.encoding()).isEqualTo("UTF-8");
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
        @DisplayName("IsQuiet Tests")
        class IsQuietTests {

            @Test
            void isQuietDefaultsFalse() {
                var op = new JacocoReportOperation();
                assertThat(op.isQuiet()).isFalse();
            }

            @Test
            void isQuietMatchesSilent() {
                var op = new JacocoReportOperation().silent(true);
                assertThat(op.isQuiet()).isTrue();
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
        @DisplayName("Report Name Tests")
        class ReportNameTests {

            @Test
            void customReportName() {
                var op = new JacocoReportOperation();
                op.name("Custom");
                assertThat(op.name()).isEqualTo("Custom");
            }

            @Test
            void defaultReportName() {
                var op = new JacocoReportOperation();
                assertThat(op.name()).isEqualTo("JaCoCo Coverage Report");
            }
        }

        @Nested
        @DisplayName("Tab Width Tests")
        class TabWidthTests {

            @Test
            void customTabWidth() {
                var op = new JacocoReportOperation();
                op.tabWidth(2);
                assertThat(op.tabWidth()).isEqualTo(2);
            }

            @Test
            void defaultTabWidth() {
                var op = new JacocoReportOperation();
                assertThat(op.tabWidth()).isEqualTo(4);
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

    @Nested
    @DisplayName("Report Enable/Disable Tests")
    class ReportEnableDisableTests {

        @Test
        void csvDisabledByDefaultIsFalse() {
            var op = new JacocoReportOperation();
            assertThat(op.isCsvDisabled()).isFalse();
        }

        private void deleteRecursively(File dir) throws IOException {
            if (dir.exists()) {
                try (var paths = Files.walk(dir.toPath())) {
                    paths.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
        }

        @Test
        void disableAllSkipsAnalysisAndDoesNotCreateDirs() throws Exception {
            var project = new BaseProjectBlueprint(
                    tempDir,
                    "com.example",
                    "test",
                    "Test");

            var reportsDir = Path.of(project.buildDirectory().getPath(), "reports", "jacoco", "test").toFile();
            var execDir = Path.of(project.buildDirectory().getPath(), "jacoco").toFile();

            deleteRecursively(reportsDir);
            deleteRecursively(execDir);

            var op = new JacocoReportOperation()
                    .fromProject(project)
                    .disableCsv()
                    .disableHtml()
                    .disableXml();

            // execute() will throw because execFiles is empty, so it tries to run tests
            // and fails finding the agent before the disable-check
            assertThatThrownBy(op::execute)
                    .isInstanceOf(ExitStatusException.class);

            assertThat(testLogHandler.containsMessage("JaCoCo agent does not exist")).isTrue();

            // Directories still shouldn't be created even on failure
            assertThat(reportsDir).doesNotExist();
            assertThat(execDir).doesNotExist();

        }

        @Test
        void disableCsvOnlyGeneratesHtmlAndXml() throws Exception {
            var op = newJacocoReportOperation().disableCsv();
            op.execute();
            testLogHandler.printLogMessages();

            assertThat(csvFile).doesNotExist();
            assertThat(xmlFile).exists();
            assertThat(htmlDir).isDirectory();
            assertThat(Path.of(htmlDir.getPath(), "index.html")).exists();
        }

        @Test
        void disableCsvSetsFlag() {
            var op = new JacocoReportOperation().disableCsv();
            assertThat(op.isCsvDisabled()).isTrue();
        }

        @Test
        void disableHtmlOnlyGeneratesCsvAndXml() throws Exception {
            var op = newJacocoReportOperation().disableHtml();
            op.execute();
            testLogHandler.printLogMessages();

            assertThat(csvFile).exists();
            assertThat(xmlFile).exists();
            assertThat(htmlDir).doesNotExist();
        }

        @Test
        void disableHtmlSetsFlag() {
            var op = new JacocoReportOperation().disableHtml();
            assertThat(op.isHtmlDisabled()).isTrue();
        }

        @Test
        void disableXmlOnlyGeneratesCsvAndHtml() throws Exception {
            var op = newJacocoReportOperation().disableXml();
            op.execute();
            testLogHandler.printLogMessages();

            assertThat(csvFile).exists();
            assertThat(xmlFile).doesNotExist();
            assertThat(htmlDir).isDirectory();
        }

        @Test
        void disableXmlSetsFlag() {
            var op = new JacocoReportOperation().disableXml();
            assertThat(op.isXmlDisabled()).isTrue();
        }

        @Test
        void enableAllReportsAfterDisableGeneratesEverything() throws Exception {
            var op = newJacocoReportOperation()
                    .disableCsv()
                    .disableHtml()
                    .disableXml()
                    .enableAllReports();

            op.execute();
            testLogHandler.printLogMessages();

            assertThat(csvFile).exists();
            assertThat(xmlFile).exists();
            assertThat(htmlDir).isDirectory();
        }

        @Test
        void enableAllReportsClearsAllFlags() {
            var op = new JacocoReportOperation()
                    .disableCsv()
                    .disableHtml()
                    .disableXml()
                    .enableAllReports();

            assertThat(op.isCsvDisabled()).isFalse();
            assertThat(op.isHtmlDisabled()).isFalse();
            assertThat(op.isXmlDisabled()).isFalse();
        }

        @Test
        void enableCsvClearsFlag() {
            var op = new JacocoReportOperation().disableCsv().enableCsv();
            assertThat(op.isCsvDisabled()).isFalse();
        }

        @Test
        void enableHtmlClearsFlag() {
            var op = new JacocoReportOperation().disableHtml().enableHtml();
            assertThat(op.isHtmlDisabled()).isFalse();
        }

        @Test
        void enableXmlClearsFlag() {
            var op = new JacocoReportOperation().disableXml().enableXml();
            assertThat(op.isXmlDisabled()).isFalse();
        }

        @Test
        void fluentChainingReturnsSameInstance() {
            var op = new JacocoReportOperation();
            assertThat(op.disableCsv()).isSameAs(op);
            assertThat(op.disableHtml()).isSameAs(op);
            assertThat(op.disableXml()).isSameAs(op);
            assertThat(op.enableCsv()).isSameAs(op);
            assertThat(op.enableHtml()).isSameAs(op);
            assertThat(op.enableXml()).isSameAs(op);
            assertThat(op.enableAllReports()).isSameAs(op);
        }

        @Test
        void htmlDisabledByDefaultIsFalse() {
            var op = new JacocoReportOperation();
            assertThat(op.isHtmlDisabled()).isFalse();
        }

        JacocoReportOperation newJacocoReportOperation() {
            return new JacocoReportOperation()
                    .fromProject(new BaseProjectBlueprint(
                            new File("examples"),
                            "com.example",
                            "examples",
                            "Examples"))
                    .csv(csvFile)
                    .html(htmlDir)
                    .xml(xmlFile)
                    .classFiles(new File("src/test/resources/Examples.class"))
                    .sourceFiles(new File("examples/src/main/java"))
                    .execFiles(new File("src/test/resources/jacoco.exec"));
        }

        @Test
        void xmlDisabledByDefaultIsFalse() {
            var op = new JacocoReportOperation();
            assertThat(op.isXmlDisabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    @SuppressWarnings("DataFlowIssue")
    class ValidationTests {

        @ParameterizedTest
        @EmptySource
        void classFilesWithEmpty(String arg) {
            assertThatThrownBy(() -> new JacocoReportOperation().classFiles("foo", arg))
                    .as("array has empty element").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().classFilesStrings(List.of("foo", arg)))
                    .as("list has empty element").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().classFilesStrings(List.of()))
                    .as("list is empty").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().classFiles(arg))
                    .as("varargs empty").isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @NullSource
        void classFilesWithNull(String arg) {
            assertThatThrownBy(() -> new JacocoReportOperation().classFiles("foo", arg))
                    .as("array has null element").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().classFilesStrings(List.of("foo", arg)))
                    .as("list has null element").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().classFiles((File[]) null))
                    .as("array is null").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().classFiles((Collection<File>) null))
                    .as("collection is null").isInstanceOf(NullPointerException.class);
        }

        @Test
        void encodingWithNullOrEmpty() {
            assertThatThrownBy(() -> new JacocoReportOperation().encoding(null))
                    .as("encoding null").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().encoding(""))
                    .as("encoding empty").isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @EmptySource
        void excludesWithEmpty(String arg) {
            assertThatThrownBy(() -> new JacocoReportOperation().excludes("foo", arg))
                    .as("array has empty element").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().excludes(List.of("foo", arg)))
                    .as("list has empty element").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().excludes(List.of()))
                    .as("list is empty").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().excludes(""))
                    .as("varargs empty").isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @NullSource
        void excludesWithNull(String arg) {
            assertThatThrownBy(() -> new JacocoReportOperation().excludes("foo", arg))
                    .as("array has null element").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().excludes(List.of("foo", arg)))
                    .as("list has null element").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().excludes((String[]) null))
                    .as("array is null").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().excludes((Collection<String>) null))
                    .as("collection is null").isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @EmptySource
        void execFilesWithEmpty(String arg) {
            assertThatThrownBy(() -> new JacocoReportOperation().execFiles("foo", arg))
                    .as("array has empty element").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().execFilesStrings(List.of("foo", arg)))
                    .as("list has empty element").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().execFilesStrings(List.of()))
                    .as("list is empty").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().execFiles(arg))
                    .as("varargs empty").isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @NullSource
        void execFilesWithNull(String arg) {
            assertThatThrownBy(() -> new JacocoReportOperation().execFiles("foo", arg))
                    .as("array has null element").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().execFilesStrings(List.of("foo", arg)))
                    .as("list has null element").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().execFiles((File[]) null))
                    .as("array is null").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().execFiles((Collection<File>) null))
                    .as("collection is null").isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @EmptySource
        void fileSettersWithEmpty(String arg) {
            assertThatThrownBy(() -> new JacocoReportOperation().csv(arg))
                    .as("csv string empty").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().html(arg))
                    .as("html string empty").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().xml(arg))
                    .as("xml string empty").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().destFile(arg))
                    .as("destFile string empty").isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @NullSource
        void fileSettersWithNull(File file) {
            assertThatThrownBy(() -> new JacocoReportOperation().csv(file))
                    .as("csv file null").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().html(file))
                    .as("html file null").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().xml(file))
                    .as("xml file null").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().destFile(file))
                    .as("destFile file null").isInstanceOf(NullPointerException.class);
        }

        @Test
        void fromProjectWithNull() {
            assertThatThrownBy(() -> new JacocoReportOperation().fromProject(null))
                    .as("project null").isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @EmptySource
        void includesWithEmpty(String arg) {
            assertThatThrownBy(() -> new JacocoReportOperation().includes("foo", arg))
                    .as("array has empty element").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().includes(List.of("foo", arg)))
                    .as("list has empty element").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().includes(List.of()))
                    .as("list is empty").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().includes(arg))
                    .as("varargs empty").isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @NullSource
        void includesWithNull(String arg) {
            assertThatThrownBy(() -> new JacocoReportOperation().includes("foo", arg))
                    .as("array has null element").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().includes(List.of("foo", arg)))
                    .as("list has null element").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().includes((String[]) null))
                    .as("array is null").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().includes((Collection<String>) null))
                    .as("collection is null").isInstanceOf(NullPointerException.class);
        }

        @Test
        void nameWithNullOrEmpty() {
            assertThatThrownBy(() -> new JacocoReportOperation().name(null))
                    .as("name null").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().name(""))
                    .as("name empty").isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @EmptySource
        void sourceFilesWithEmpty(String arg) {
            assertThatThrownBy(() -> new JacocoReportOperation().sourceFiles("foo", arg))
                    .as("array has empty element").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().sourceFilesStrings(List.of("foo", arg)))
                    .as("list has empty element").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().sourceFilesStrings(List.of()))
                    .as("list is empty").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().sourceFiles(arg))
                    .as("varargs empty").isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @NullSource
        void sourceFilesWithNull(String arg) {
            assertThatThrownBy(() -> new JacocoReportOperation().sourceFiles("foo", arg))
                    .as("array has null element").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().sourceFilesStrings(List.of("foo", arg)))
                    .as("list has null element").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().sourceFiles((File[]) null))
                    .as("array is null").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().sourceFiles((Collection<File>) null))
                    .as("collection is null").isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -10})
        void tabWidthWithNonPositive(int width) {
            assertThatThrownBy(() -> new JacocoReportOperation().tabWidth(width))
                    .as("tabWidth must be positive").isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void testOperationWithNull() {
            assertThatThrownBy(() -> new JacocoReportOperation().testOperation(null))
                    .as("testOperation null").isInstanceOf(NullPointerException.class);
        }

        @Test
        void testToolOptionsWithNullOrEmpty() {
            assertThatThrownBy(() -> new JacocoReportOperation().testToolOptions((String[]) null))
                    .as("array null").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().testToolOptions((Collection<String>) null))
                    .as("collection null").isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().testToolOptions(""))
                    .as("varargs empty").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().testToolOptions("foo", ""))
                    .as("has empty element").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JacocoReportOperation().testToolOptions("foo", null))
                    .as("has null element").isInstanceOf(NullPointerException.class);
        }
    }
}
