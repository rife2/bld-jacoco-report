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
import org.junit.jupiter.api.Test;
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
    void checkAllParamsTest() throws IOException {
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
    void executeFailureTest() {
        var op = new JacocoReportOperation().fromProject(new Project());
        assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);
    }

    @Test
    void executeTest() throws Exception {
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
    void testClassFiles() {
        var foo = new File("foo");
        var bar = new File("bar");

        var op = new JacocoReportOperation().classFiles("foo", "bar");
        assertThat(op.classFiles()).as("String...").contains(foo, bar);
        op.classFiles().clear();

        op = op.classFiles(foo, bar);
        assertThat(op.classFiles()).as("File...").contains(foo, bar);
        op.classFiles().clear();

        op = op.classFiles(foo.toPath(), bar.toPath());
        assertThat(op.classFiles()).as("Path...").contains(foo, bar);
        op.classFiles().clear();

        op = op.classFilesStrings(List.of("foo", "bar"));
        assertThat(op.classFiles()).as("List(String...)").contains(foo, bar);
        op.classFiles().clear();

        op = op.classFiles(List.of(foo, bar));
        assertThat(op.classFiles()).as("File...").contains(foo, bar);
        op.classFiles().clear();

        op = op.classFilesPaths(List.of(foo.toPath(), bar.toPath()));
        assertThat(op.classFiles()).as("Path...").contains(foo, bar);
        op.classFiles().clear();
    }

    @Test
    void testExecFiles() {
        var foo = new File("foo");
        var bar = new File("bar");

        var op = new JacocoReportOperation().execFiles("foo", "bar");
        assertThat(op.execFiles()).as("String...").contains(foo, bar);
        op.execFiles().clear();

        op = op.execFiles(foo, bar);
        assertThat(op.execFiles()).as("File...").contains(foo, bar);
        op.execFiles().clear();

        op = op.execFiles(foo.toPath(), bar.toPath());
        assertThat(op.execFiles()).as("Path...").contains(foo, bar);
        op.execFiles().clear();

        op = op.execFilesStrings(List.of("foo", "bar"));
        assertThat(op.execFiles()).as("List(String...)").contains(foo, bar);
        op.execFiles().clear();

        op = op.execFiles(List.of(foo, bar));
        assertThat(op.execFiles()).as("File...").contains(foo, bar);
        op.execFiles().clear();

        op = op.execFilesPaths(List.of(foo.toPath(), bar.toPath()));
        assertThat(op.execFiles()).as("Path...").contains(foo, bar);
        op.execFiles().clear();
    }

    @Test
    void testSourceFiles() {
        var foo = new File("foo");
        var bar = new File("bar");

        var op = new JacocoReportOperation().sourceFiles("foo", "bar");
        assertThat(op.sourceFiles()).as("String...").contains(foo, bar);
        op.sourceFiles().clear();

        op = op.sourceFiles(foo, bar);
        assertThat(op.sourceFiles()).as("File...").contains(foo, bar);
        op.sourceFiles().clear();

        op = op.sourceFiles(foo.toPath(), bar.toPath());
        assertThat(op.sourceFiles()).as("Path...").contains(foo, bar);
        op.sourceFiles().clear();

        op = op.sourceFilesStrings(List.of("foo", "bar"));
        assertThat(op.sourceFiles()).as("List(String...)").contains(foo, bar);
        op.sourceFiles().clear();

        op = op.sourceFiles(List.of(foo, bar));
        assertThat(op.sourceFiles()).as("File...").contains(foo, bar);
        op.sourceFiles().clear();

        op = op.sourceFilesPaths(List.of(foo.toPath(), bar.toPath()));
        assertThat(op.sourceFiles()).as("Path...").contains(foo, bar);
        op.sourceFiles().clear();
    }
}
