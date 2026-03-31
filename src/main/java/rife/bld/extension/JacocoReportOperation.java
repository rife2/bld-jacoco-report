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
import rife.bld.extension.tools.CollectionTools;
import rife.bld.extension.tools.IOTools;
import rife.bld.operations.AbstractOperation;
import rife.bld.operations.TestOperation;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
     * The directory of the HTML report output.
     */
    private File htmlDirectory_;
    /**
     * The project reference.
     */
    private BaseProject project_;
    /**
     * The report name.
     */
    private String reportName_ = "JaCoCo Coverage Report";
    /**
     * The tab width.
     */
    private int tabWidth_ = 4;
    /**
     * The test operation.
     */
    private TestOperation<?, ?> testOperation_;
    /**
     * The location of the XML report.
     */
    private File xml_;

    /**
     * Performs the operation execution that can be wrapped by the {@code #executeOnce} call.
     */
    @Override
    @SuppressFBWarnings("STT_STRING_PARSING_A_FIELD")
    public void execute() throws Exception {
        if (project_ == null) {
            if (LOGGER.isLoggable(Level.SEVERE) && !silent()) {
                LOGGER.severe("A project must be specified.");
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }

        var buildJacocoReportsDir =
                Path.of(project_.buildDirectory().getPath(), "reports", "jacoco", "test").toFile();
        var buildJacocoExecDir = Path.of(project_.buildDirectory().getPath(), "jacoco").toFile();
        var buildJacocoExec = Path.of(buildJacocoExecDir.getPath(), "jacoco.exec").toFile();

        // Resolve destFile locally to avoid mutating instance state
        var effectiveDestFile = (destFile_ != null) ? destFile_ : buildJacocoExec;

        // Resolve exec files locally to avoid mutating execFiles_ as a side effect
        List<File> effectiveExecFiles = new ArrayList<>(execFiles_);

        if (effectiveExecFiles.isEmpty()) {
            var testOp = (testOperation_ != null)
                    ? testOperation_
                    : project_.testOperation().fromProject(project_);

            var jacocoVersion = JaCoCo.VERSION;
            var lastDot = jacocoVersion.lastIndexOf('.');
            if (lastDot <= 0) {
                throw new IllegalStateException("Unexpected JaCoCo version format: " + jacocoVersion);
            }
            var shortVersion = jacocoVersion.substring(0, lastDot);

            testOp.javaOptions().javaAgent(
                    Path.of(project_.libBldDirectory().getPath(),
                            "org.jacoco.agent-" + shortVersion + "-runtime.jar").toFile(),
                    "destfile=" + effectiveDestFile.getPath());

            if (!testToolOptions_.isEmpty()) {
                testOp.testToolOptions().addAll(testToolOptions_);
            }

            testOp.execute();

            if (LOGGER.isLoggable(Level.INFO) && !silent()) {
                LOGGER.log(Level.INFO, "Execution Data: {0}", effectiveDestFile);
            }

            if (buildJacocoExec.exists()) {
                effectiveExecFiles.add(buildJacocoExec);
            }
        }

        // Resolve source files locally
        var effectiveSourceFiles = sourceFiles_.isEmpty()
                ? List.of(project_.srcMainJavaDirectory())
                : List.copyOf(sourceFiles_);

        // Resolve class files locally
        var effectiveClassFiles = classFiles_.isEmpty()
                ? List.of(project_.buildMainDirectory())
                : List.copyOf(classFiles_);

        var effectiveHtmlDir = (htmlDirectory_ != null)
                ? htmlDirectory_
                : new File(buildJacocoReportsDir, "html");

        var effectiveXml = (xml_ != null)
                ? xml_
                : new File(buildJacocoReportsDir, "jacocoTestReport.xml");

        var effectiveCsv = (csv_ != null)
                ? csv_
                : new File(buildJacocoReportsDir, "jacocoTestReport.csv");

        if (!IOTools.mkdirs(buildJacocoReportsDir)) {
            if (LOGGER.isLoggable(Level.SEVERE) && !silent()) {
                LOGGER.severe("Could not create reports directory: " + buildJacocoReportsDir.getAbsolutePath());
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
        if (!IOTools.mkdirs(buildJacocoExecDir)) {
            if (LOGGER.isLoggable(Level.SEVERE) && !silent()) {
                LOGGER.severe("Could not create directory: " + buildJacocoExecDir.getAbsolutePath());
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }

        var loader = loadExecFiles(effectiveExecFiles);
        var bundle = analyze(loader.getExecutionDataStore(), effectiveClassFiles);
        writeReports(bundle, loader, effectiveXml, effectiveCsv, effectiveHtmlDir, effectiveSourceFiles);
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @see #classFiles(Collection...)
     */
    public JacocoReportOperation classFiles(File... classFiles) {
        classFiles_.addAll(CollectionTools.combine(classFiles));
        return this;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @see #classFilesStrings(Collection...)
     */
    public JacocoReportOperation classFiles(String... classFiles) {
        classFiles_.addAll(CollectionTools.combineStringsToFiles(classFiles));
        return this;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @see #classFilesPaths(Collection...)
     */
    public JacocoReportOperation classFiles(Path... classFiles) {
        classFiles_.addAll(CollectionTools.combinePathsToFiles(classFiles));
        return this;
    }

    /**
     * Returns the locations of Java class files.
     * <p>
     * The returned list is the live internal list and is mutable by design
     * to support the fluent builder pattern.
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
    @SafeVarargs
    public final JacocoReportOperation classFiles(Collection<File>... classFiles) {
        classFiles_.addAll(CollectionTools.combine(classFiles));
        return this;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @see #classFiles(Path...)
     */
    @SafeVarargs
    public final JacocoReportOperation classFilesPaths(Collection<Path>... classFiles) {
        classFiles_.addAll(CollectionTools.combinePathsToFiles(classFiles));
        return this;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @see #classFiles(String...)
     */
    @SafeVarargs
    public final JacocoReportOperation classFilesStrings(Collection<String>... classFiles) {
        classFiles_.addAll(CollectionTools.combineStringsToFiles(classFiles));
        return this;
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
     * @see #execFiles(Collection...)
     */
    public JacocoReportOperation execFiles(File... execFiles) {
        execFiles_.addAll(CollectionTools.combine(execFiles));
        return this;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @see #execFilesStrings(Collection...)
     */
    public JacocoReportOperation execFiles(String... execFiles) {
        execFiles_.addAll(CollectionTools.combineStringsToFiles(execFiles));
        return this;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @see #execFilesPaths(Collection...)
     */
    public JacocoReportOperation execFiles(Path... execFiles) {
        execFiles_.addAll(CollectionTools.combinePathsToFiles(execFiles));
        return this;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @see #execFiles(File...)
     */
    @SafeVarargs
    public final JacocoReportOperation execFiles(Collection<File>... execFiles) {
        execFiles_.addAll(CollectionTools.combine(execFiles));
        return this;
    }

    /**
     * Returns the locations of the JaCoCo *.exec files to read.
     * <p>
     * The returned list is the live internal list and is mutable by design
     * to support the fluent builder pattern.
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
    @SafeVarargs
    public final JacocoReportOperation execFilesPaths(Collection<Path>... execFiles) {
        execFiles_.addAll(CollectionTools.combinePathsToFiles(execFiles));
        return this;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @see #execFiles(String...)
     */
    @SafeVarargs
    public final JacocoReportOperation execFilesStrings(Collection<String>... execFiles) {
        execFiles_.addAll(CollectionTools.combineStringsToFiles(execFiles));
        return this;
    }

    /**
     * Configure the operation from a {@link BaseProject}.
     *
     * @param project the project
     * @return this operation instance
     */
    public JacocoReportOperation fromProject(BaseProject project) {
        project_ = Objects.requireNonNull(project, "The project must not be null");
        return this;
    }

    /**
     * Sets the directory for the HTML report output.
     *
     * @param html the HTML report directory
     * @return this operation instance
     */
    public JacocoReportOperation html(File html) {
        htmlDirectory_ = html;
        return this;
    }

    /**
     * Sets the directory for the HTML report output.
     *
     * @param html the HTML report directory
     * @return this operation instance
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JacocoReportOperation html(String html) {
        return html(new File(html));
    }

    /**
     * Sets the directory for the HTML report output.
     *
     * @param html the HTML report directory
     * @return this operation instance
     */
    public JacocoReportOperation html(Path html) {
        return html(html.toFile());
    }

    /**
     * Returns the HTML report output directory.
     *
     * @return the HTML report directory
     */
    public File html() {
        return htmlDirectory_;
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
     * @see #sourceFiles(Collection...)
     */
    public JacocoReportOperation sourceFiles(File... sourceFiles) {
        sourceFiles_.addAll(CollectionTools.combine(sourceFiles));
        return this;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @see #sourceFilesStrings(Collection...)
     */
    public JacocoReportOperation sourceFiles(String... sourceFiles) {
        sourceFiles_.addAll(CollectionTools.combineStringsToFiles(sourceFiles));
        return this;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @see #sourceFilesPaths(Collection...)
     */
    public JacocoReportOperation sourceFiles(Path... sourceFiles) {
        sourceFiles_.addAll(CollectionTools.combinePathsToFiles(sourceFiles));
        return this;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @see #sourceFiles(File...)
     */
    @SafeVarargs
    public final JacocoReportOperation sourceFiles(Collection<File>... sourceFiles) {
        sourceFiles_.addAll(CollectionTools.combine(sourceFiles));
        return this;
    }

    /**
     * Returns the locations of the source files.
     * <p>
     * The returned list is the live internal list and is mutable by design
     * to support the fluent builder pattern.
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
    @SafeVarargs
    public final JacocoReportOperation sourceFilesPaths(Collection<Path>... sourceFiles) {
        sourceFiles_.addAll(CollectionTools.combinePathsToFiles(sourceFiles));
        return this;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @see #sourceFiles(String...)
     */
    @SafeVarargs
    public final JacocoReportOperation sourceFilesStrings(Collection<String>... sourceFiles) {
        sourceFiles_.addAll(CollectionTools.combineStringsToFiles(sourceFiles));
        return this;
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
     * Sets the test operation.
     *
     * @param testOperation the test operation; must not be null
     * @return this operation instance
     */
    public JacocoReportOperation testOperation(TestOperation<?, ?> testOperation) {
        testOperation_ = Objects.requireNonNull(testOperation, "testOperation must not be null");
        return this;
    }

    /**
     * Returns the test tool options.
     * <p>
     * The returned list is the live internal list and is mutable by design
     * to support the fluent builder pattern.
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
     * @param options the options to set
     * @return this operation instance
     * @see #testToolOptions(Collection...)
     */
    public JacocoReportOperation testToolOptions(String... options) {
        testToolOptions_.addAll(CollectionTools.combine(options));
        return this;
    }

    /**
     * Sets the test tool options.
     *
     * @param options the options to set
     * @return this operation instance
     * @see #testToolOptions(String...)
     */
    @SafeVarargs
    public final JacocoReportOperation testToolOptions(Collection<String>... options) {
        testToolOptions_.addAll(CollectionTools.combine(options));
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

    private IBundleCoverage analyze(ExecutionDataStore data, List<File> classFiles) throws IOException {
        var builder = new CoverageBuilder();
        var analyzer = new Analyzer(data, builder);
        for (var f : classFiles) {
            if (LOGGER.isLoggable(Level.INFO) && !silent()) {
                LOGGER.info(f.getAbsolutePath());
            }
            analyzer.analyzeAll(f);
        }
        return builder.getBundle(reportName_);
    }

    private ExecFileLoader loadExecFiles(List<File> execFiles) throws IOException {
        var loader = new ExecFileLoader();
        if (execFiles.isEmpty() && LOGGER.isLoggable(Level.WARNING) && !silent()) {
            LOGGER.warning("No execution data files provided.");
        } else {
            for (var f : execFiles) {
                if (!f.exists()) {
                    if (LOGGER.isLoggable(Level.WARNING) && !silent()) {
                        LOGGER.warning("Exec file not found, skipping: " + f.getAbsolutePath());
                    }
                    continue;
                }
                if (LOGGER.isLoggable(Level.INFO) && !silent()) {
                    LOGGER.log(Level.INFO, "Loading execution data: {0}", f.getAbsolutePath());
                }
                loader.load(f);
            }
        }
        return loader;
    }

    /**
     * Creates a multi-format report visitor writing to XML, CSV, and HTML outputs.
     * <p>
     * The output streams are owned by the returned {@link IReportVisitor}; calling
     * {@link IReportVisitor#visitEnd()} is required to flush and close them.
     */
    private IReportVisitor reportVisitor(File xml, File csv, File htmlDirectory) throws IOException {
        var visitors = new ArrayList<IReportVisitor>();

        @SuppressWarnings("PMD.CloseResource")
        var xmlStream = Files.newOutputStream(xml.toPath());
        try {
            visitors.add(new XMLFormatter().createVisitor(xmlStream));
        } catch (IOException e) {
            xmlStream.close();
            throw e;
        }

        @SuppressWarnings("PMD.CloseResource")
        var csvStream = Files.newOutputStream(csv.toPath());
        try {
            visitors.add(new CSVFormatter().createVisitor(csvStream));
        } catch (IOException e) {
            csvStream.close();
            throw e;
        }

        visitors.add(new HTMLFormatter().createVisitor(new FileMultiReportOutput(htmlDirectory)));

        return new MultiReportVisitor(visitors);
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    @SuppressFBWarnings("OCP_OVERLY_CONCRETE_PARAMETER")
    private ISourceFileLocator sourceLocator(List<File> sourceFiles) {
        var multi = new MultiSourceFileLocator(tabWidth_);
        for (var f : sourceFiles) {
            multi.add(new DirectorySourceFileLocator(f, encoding_, tabWidth_));
        }
        return multi;
    }

    private void writeReports(IBundleCoverage bundle, ExecFileLoader loader,
                              File xml, File csv, File htmlDirectory, List<File> sourceFiles)
            throws IOException {
        if (LOGGER.isLoggable(Level.INFO) && !silent()) {
            LOGGER.log(Level.INFO, "Analyzing {0} classes.",
                    bundle.getClassCounter().getTotalCount());
        }
        var visitor = reportVisitor(xml, csv, htmlDirectory);
        visitor.visitInfo(loader.getSessionInfoStore().getInfos(),
                loader.getExecutionDataStore().getContents());
        visitor.visitBundle(bundle, sourceLocator(sourceFiles));
        // visitEnd() flushes and closes all underlying output streams
        visitor.visitEnd();
        if (LOGGER.isLoggable(Level.INFO) && !silent()) {
            LOGGER.log(Level.INFO, "XML Report: file://{0}", xml.toURI().getPath());
            LOGGER.log(Level.INFO, "CSV Report: file://{0}", csv.toURI().getPath());
            LOGGER.log(Level.INFO, "HTML Report: file://{0}index.html", htmlDirectory.toURI().getPath());
        }
    }
}