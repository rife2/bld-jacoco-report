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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jacoco.core.JaCoCo;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.*;
import org.jacoco.report.csv.CSVFormatter;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;
import rife.bld.BaseProject;
import rife.bld.extension.tools.IOUtils;
import rife.bld.operations.AbstractOperation;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Generates <a href="https://www.jacoco.org/jacoco">JaCoCo</a> reports.
 *
 * @author <a href="https://erik.thauvin.net/">Erik C. Thauvin</a>
 * @since 1.0
 */
public class JacocoReportOperation extends AbstractOperation<JacocoReportOperation> {
    private static final Logger LOGGER = Logger.getLogger(JacocoReportOperation.class.getName());
    /**
     * The location of the java class files.
     */
    private final List<File> classFiles_ = new ArrayList<>();
    /**
     * The location of the exec files.
     */
    private final List<File> execFiles_ = new ArrayList<>();
    /**
     * The location of the source files.
     */
    private final List<File> sourceFiles_ = new ArrayList<>();
    /**
     * The test tool options.
     */
    private final List<String> testToolOptions_ = new ArrayList<>();
    /**
     * The location of the CSV report.
     */
    private File csv_;
    /**
     * The file to write execution data to.
     */
    private File destFile_;
    /**
     * The source file encoding.
     */
    private String encoding_;
    /**
     * The location of the HTML report.
     */
    private File html_;
    /**
     * The project reference.
     */
    private BaseProject project_;
    /**
     * The report name.
     */
    private String reportName_ = "JaCoCo Coverage Report";
    /**
     * THe tab width.
     */
    private int tabWidth_ = 4;
    /**
     * THe location of the XML report
     */
    private File xml_;

    /**
     * Performs the operation execution that can be wrapped by the {@code #executeOnce} call.
     */
    @Override
    @SuppressFBWarnings("STT_STRING_PARSING_A_FIELD")
    public void execute() throws Exception {
        if ((project_ == null)) {
            if (LOGGER.isLoggable(Level.SEVERE) && !silent()) {
                LOGGER.severe("A project must be specified.");
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }

        var buildJacocoReportsDir = Path.of(project_.buildDirectory().getPath(), "reports", "jacoco", "test").toFile();
        var buildJacocoExecDir = Path.of(project_.buildDirectory().getPath(), "jacoco").toFile();
        var buildJacocoExec = Path.of(buildJacocoExecDir.getPath(), "jacoco.exec").toFile();

        if (destFile_ == null) {
            destFile_ = buildJacocoExec;
        }

        if (execFiles_.isEmpty()) {
            var testOperation = project_.testOperation().fromProject(project_);
            testOperation.javaOptions().javaAgent(Path.of(project_.libBldDirectory().getPath(),
                    "org.jacoco.agent-" + JaCoCo.VERSION.substring(0, JaCoCo.VERSION.lastIndexOf('.'))
                            + "-runtime.jar").toFile(), "destfile=" + destFile_.getPath());

            if (!testToolOptions_.isEmpty()) {
                testOperation.testToolOptions().addAll(testToolOptions_);
            }

            testOperation.execute();

            if (LOGGER.isLoggable(Level.INFO) && !silent()) {
                LOGGER.log(Level.INFO, "Execution Data: {0}", destFile_);
            }

            if (buildJacocoExec.exists()) {
                execFiles_.add(buildJacocoExec);
            }
        }

        if (sourceFiles_.isEmpty()) {
            sourceFiles_.add(project_.srcMainJavaDirectory());
        }

        if (classFiles_.isEmpty()) {
            classFiles_.add(project_.buildMainDirectory());
        }

        if (html_ == null) {
            html_ = new File(buildJacocoReportsDir, "html");
        }

        if (xml_ == null) {
            xml_ = new File(buildJacocoReportsDir, "jacocoTestReport.xml");
        }

        if (csv_ == null) {
            csv_ = new File(buildJacocoReportsDir, "jacocoTestReport.csv");
        }

        if (!IOUtils.mkdirs(buildJacocoReportsDir)) {
            if (LOGGER.isLoggable(Level.SEVERE) && !silent()) {
                LOGGER.severe("Could not create reports directory: " + buildJacocoReportsDir.getAbsolutePath());
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
        if (!IOUtils.mkdirs(buildJacocoExecDir)) {
            if (LOGGER.isLoggable(Level.SEVERE) && !silent()) {
                LOGGER.severe("Could not create directory: " + buildJacocoExecDir.getAbsolutePath());
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }

        var loader = loadExecFiles();
        var bundle = analyze(loader.getExecutionDataStore());
        writeReports(bundle, loader);
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @see #classFiles(Collection)
     */
    public JacocoReportOperation classFiles(File... classFiles) {
        return classFiles(List.of(classFiles));
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @see #classFilesStrings(Collection)
     */
    public JacocoReportOperation classFiles(String... classFiles) {
        return classFilesStrings(List.of(classFiles));
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @see #classFilesPaths(Collection)
     */
    public JacocoReportOperation classFiles(Path... classFiles) {
        return classFilesPaths(List.of(classFiles));
    }

    /**
     * Returns the locations of Java class files.
     *
     * @return the class files
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public List<File> classFiles() {
        return classFiles_;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @see #classFiles(File...)
     */
    public JacocoReportOperation classFiles(Collection<File> classFiles) {
        classFiles_.addAll(classFiles);
        return this;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @see #classFiles(Path...)
     */
    public JacocoReportOperation classFilesPaths(Collection<Path> classFiles) {
        return classFiles(classFiles.stream().map(Path::toFile).toList());
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @see #classFiles(String...)
     */
    public JacocoReportOperation classFilesStrings(Collection<String> classFiles) {
        return classFiles(classFiles.stream().map(File::new).toList());
    }

    /**
     * Sets the location of the CSV report.
     *
     * @param csv the report location
     * @return this operation instance
     */
    public JacocoReportOperation csv(File csv) {
        csv_ = csv;
        return this;
    }

    /**
     * Sets the location of the CSV report.
     *
     * @param csv the report location
     * @return this operation instance
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JacocoReportOperation csv(String csv) {
        return csv(new File(csv));
    }

    /**
     * Sets the location of the CSV report.
     *
     * @param csv the report location
     * @return this operation instance
     */
    public JacocoReportOperation csv(Path csv) {
        return csv(csv.toFile());
    }

    /**
     * Returns the CSV report location.
     *
     * @return the CSV report location
     */
    public File csv() {
        return csv_;
    }

    /**
     * Sets the file to write execution data to.
     *
     * @param destFile the file
     * @return this operation instance
     */
    public JacocoReportOperation destFile(File destFile) {
        destFile_ = destFile;
        return this;
    }

    /**
     * Sets the file to write execution data to.
     *
     * @param destFile the file
     * @return this operation instance
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JacocoReportOperation destFile(String destFile) {
        return destFile(new File(destFile));
    }

    /**
     * Sets the file to write execution data to.
     *
     * @param destFile the file
     * @return this operation instance
     */
    public JacocoReportOperation destFile(Path destFile) {
        return destFile(destFile.toFile());
    }

    /**
     * Returns the file to write execution data to.
     *
     * @return the file to write execution data to
     */
    public File destFile() {
        return destFile_;
    }

    /**
     * Sets the source file encoding. The platform encoding is used by default.
     *
     * @param encoding the encoding
     * @return this operation instance
     */
    public JacocoReportOperation encoding(String encoding) {
        encoding_ = encoding;
        return this;
    }

    /**
     * Returns the source file encoding.
     *
     * @return the source file encoding
     */
    public String encoding() {
        return encoding_;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @see #execFiles(Collection)
     */
    public JacocoReportOperation execFiles(File... execFiles) {
        return execFiles(List.of(execFiles));
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @see #execFilesStrings(Collection)
     */
    public JacocoReportOperation execFiles(String... execFiles) {
        return execFilesStrings(List.of(execFiles));
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @see #execFilesPaths(Collection)
     */
    public JacocoReportOperation execFiles(Path... execFiles) {
        return execFilesPaths(List.of(execFiles));
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @see #execFiles(File...)
     */
    public JacocoReportOperation execFiles(Collection<File> execFiles) {
        execFiles_.addAll(execFiles);
        return this;
    }

    /**
     * Returns the locations of the JaCoCo *.exec files to read.
     *
     * @return the exec files
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public List<File> execFiles() {
        return execFiles_;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @see #execFiles(Path...)
     */
    public JacocoReportOperation execFilesPaths(Collection<Path> execFiles) {
        return execFiles(execFiles.stream().map(Path::toFile).toList());
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @see #execFiles(String...)
     */
    public JacocoReportOperation execFilesStrings(Collection<String> execFiles) {
        return execFiles(execFiles.stream().map(File::new).toList());
    }

    /**
     * Configure the operation from a {@link BaseProject}.
     *
     * @param project the project
     * @return this operation instance
     */
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public JacocoReportOperation fromProject(BaseProject project) {
        project_ = project;
        return this;
    }

    /**
     * Sets the location of the HTML report.
     *
     * @param html the HTML
     * @return this operation instance
     */
    public JacocoReportOperation html(File html) {
        html_ = html;
        return this;
    }

    /**
     * Sets the location of the HTML report.
     *
     * @param html the HTML
     * @return this operation instance
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JacocoReportOperation html(String html) {
        return html(new File(html));
    }

    /**
     * Sets the location of the HTML report.
     *
     * @param html the HTML
     * @return this operation instance
     */
    public JacocoReportOperation html(Path html) {
        return html(html.toFile());
    }

    /**
     * Returns the HTML report location.
     *
     * @return the HTML report location
     */
    public File html() {
        return html_;
    }

    /**
     * Return the status of the quiet flag.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    public boolean isQuiet() {
        return silent();
    }

    /**
     * Sets the name used for the report.
     *
     * @param name the name
     * @return this operation instance
     */
    public JacocoReportOperation name(String name) {
        reportName_ = name;
        return this;
    }

    /**
     * Returns the name used for the report.
     *
     * @return the report name.
     */
    public String name() {
        return reportName_;
    }

    /**
     * Suppresses all output.
     *
     * @param quiet {@code true} or {@code false}
     * @return this operation instance
     */
    public JacocoReportOperation quiet(boolean quiet) {
        silent(quiet);
        return this;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @see #sourceFiles(Collection)
     */
    public JacocoReportOperation sourceFiles(File... sourceFiles) {
        return sourceFiles(List.of(sourceFiles));
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @see #sourceFilesStrings(Collection)
     */
    public JacocoReportOperation sourceFiles(String... sourceFiles) {
        return sourceFilesStrings(List.of(sourceFiles));
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @see #sourceFilesPaths(Collection)
     */
    public JacocoReportOperation sourceFiles(Path... sourceFiles) {
        return sourceFilesPaths(List.of(sourceFiles));
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @see #sourceFiles(File...)
     */
    public JacocoReportOperation sourceFiles(Collection<File> sourceFiles) {
        sourceFiles_.addAll(sourceFiles);
        return this;
    }

    /**
     * Returns the locations of the source files.
     *
     * @return the source files
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public List<File> sourceFiles() {
        return sourceFiles_;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @see #sourceFiles(Path...)
     */
    public JacocoReportOperation sourceFilesPaths(Collection<Path> sourceFiles) {
        return sourceFiles(sourceFiles.stream().map(Path::toFile).toList());
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @see #sourceFiles(String...)
     */
    public JacocoReportOperation sourceFilesStrings(Collection<String> sourceFiles) {
        return sourceFiles(sourceFiles.stream().map(File::new).toList());
    }

    /**
     * Sets the tab stop width for the source pages. Default is {@code 4}.
     *
     * @param tabWidth the tab width
     * @return this operation instance
     */
    public JacocoReportOperation tabWidth(int tabWidth) {
        tabWidth_ = tabWidth;
        return this;
    }

    /**
     * Returns the tab stop width for the source pages.
     *
     * @return the tab stop width
     */
    public int tabWidth() {
        return tabWidth_;
    }

    /**
     * Returns the test tool options.
     *
     * @return the test tool options
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public List<String> testToolOptions() {
        return testToolOptions_;
    }

    /**
     * Sets the test tool options.
     *
     * @param options The options to set
     * @return this operation instance
     * @see #testToolOptions(Collection)
     */
    public JacocoReportOperation testToolOptions(String... options) {
        return testToolOptions(List.of(options));
    }

    /**
     * Sets the test tool options.
     *
     * @param options The options to set
     * @return this operation instance
     * @see #testToolOptions(String...)
     */
    public JacocoReportOperation testToolOptions(Collection<String> options) {
        testToolOptions_.addAll(options);
        return this;
    }

    /**
     * Sets the location of the XML report.
     *
     * @param xml the report location
     * @return this operation instance
     */
    public JacocoReportOperation xml(File xml) {
        xml_ = xml;
        return this;
    }

    /**
     * Sets the location of the XML report.
     *
     * @param xml the report location
     * @return this operation instance
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JacocoReportOperation xml(String xml) {
        return xml(new File(xml));
    }

    /**
     * Sets the location of the XML report.
     *
     * @param xml the report location
     * @return this operation instance
     */
    public JacocoReportOperation xml(Path xml) {
        return xml(xml.toFile());
    }

    /**
     * Returns the XML report location.
     *
     * @return the XML report location
     */
    public File xml() {
        return xml_;
    }

    private IBundleCoverage analyze(ExecutionDataStore data) throws IOException {
        var builder = new CoverageBuilder();
        var analyzer = new Analyzer(data, builder);
        for (var f : classFiles_) {
            if (LOGGER.isLoggable(Level.INFO) && !silent()) {
                LOGGER.info(f.getAbsolutePath());
            }
            analyzer.analyzeAll(f);
        }
        return builder.getBundle(reportName_);
    }

    private ExecFileLoader loadExecFiles() throws IOException {
        var loader = new ExecFileLoader();
        if (execFiles_.isEmpty() && LOGGER.isLoggable(Level.WARNING) && !silent()) {
            LOGGER.warning("No execution data files provided.");
        } else {
            for (var f : execFiles_) {
                if (LOGGER.isLoggable(Level.INFO) && !silent()) {
                    LOGGER.log(Level.INFO, "Loading execution data: {0}",
                            f.getAbsolutePath());
                }
                loader.load(f);
            }
        }
        return loader;
    }

    private IReportVisitor reportVisitor() throws IOException {
        List<IReportVisitor> visitors = new ArrayList<>();

        visitors.add(new XMLFormatter().createVisitor(Files.newOutputStream(xml_.toPath())));
        visitors.add(new CSVFormatter().createVisitor(Files.newOutputStream(csv_.toPath())));
        visitors.add(new HTMLFormatter().createVisitor(new FileMultiReportOutput(html_)));

        return new MultiReportVisitor(visitors);
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private ISourceFileLocator sourceLocator() {
        var multi = new MultiSourceFileLocator(tabWidth_);
        for (var f : sourceFiles_) {
            multi.add(new DirectorySourceFileLocator(f, encoding_, tabWidth_));
        }
        return multi;
    }

    private void writeReports(IBundleCoverage bundle, ExecFileLoader loader)
            throws IOException {
        if (LOGGER.isLoggable(Level.INFO) && !silent()) {
            LOGGER.log(Level.INFO, "Analyzing {0} classes.",
                    bundle.getClassCounter().getTotalCount());
        }
        var visitor = reportVisitor();
        visitor.visitInfo(loader.getSessionInfoStore().getInfos(),
                loader.getExecutionDataStore().getContents());
        visitor.visitBundle(bundle, sourceLocator());
        visitor.visitEnd();
        if (LOGGER.isLoggable(Level.INFO) && !silent()) {
            LOGGER.log(Level.INFO, "XML Report: file://{0}", xml_.toURI().getPath());
            LOGGER.log(Level.INFO, "CSV Report: file://{0}", csv_.toURI().getPath());
            LOGGER.log(Level.INFO, "HTML Report: file://{0}index.html", html_.toURI().getPath());
        }
    }
}