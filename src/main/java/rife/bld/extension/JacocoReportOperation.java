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
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.*;
import org.jacoco.report.csv.CSVFormatter;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import rife.bld.BaseProject;
import rife.bld.dependencies.Dependency;
import rife.bld.extension.tools.CollectionTools;
import rife.bld.extension.tools.IOTools;
import rife.bld.extension.tools.ObjectTools;
import rife.bld.extension.tools.TextTools;
import rife.bld.operations.AbstractOperation;
import rife.bld.operations.TestOperation;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Generates <a href="https://www.jacoco.org/jacoco">JaCoCo</a> reports.
 * <p>
 * Execution modes:
 * <ul>
 *   <li><b>Run tests + generate reports:</b> Default. Uses {@link #fromProject(BaseProject)}
 *       or {@link #testOperation(TestOperation)} to run tests with the JaCoCo agent, then
 *       generates reports from the resulting execution data.</li>
 *   <li><b>Reports from existing execution data:</b> Call {@link #execFiles(File...)} with
 *       pre-generated {@code *.exec} files. Tests are not executed.</li>
 *   <li><b>Merge + run tests:</b> Call both {@link #execFiles(File...)} and
 *       {@link #testOperation(TestOperation)}. New execution data is generated and merged
 *       with the provided files before reporting.</li>
 * </ul>
 * <p>
 * Note: the live internal lists returned by {@link #classFiles()}, {@link #execFiles()},
 * {@link #sourceFiles()}, etc. are mutable by design to support the fluent builder pattern.
 * Modifying them  may cause unexpected behavior.
 *
 * @author <a href="https://erik.thauvin.net/">Erik C. Thauvin</a>
 * @since 1.0
 */
@NullMarked
@SuppressWarnings("PMD.ExcessiveImports")
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "Builder pattern intentionally exposes mutable collections"
)
public class JacocoReportOperation extends AbstractOperation<JacocoReportOperation> {

    public static final String CLASS_FILES = "classFiles";
    public static final String EXEC_FILES = "execFiles";
    public static final String SOURCE_FILES = "sourceFiles";

    private static final String HTML = "html";
    /**
     * The instance logger.
     */
    private static final Logger logger = Logger.getLogger(JacocoReportOperation.class.getName());
    /**
     * The location of the java class files.
     */
    private final List<File> classFiles_ = new ArrayList<>();
    /**
     * Class name exclude patterns.
     */
    private final List<String> excludes_ = new ArrayList<>();
    /**
     * The location of the exec files.
     */
    private final List<File> execFiles_ = new ArrayList<>();
    /**
     * Class name include patterns, e.g. {@code com/myapp/**}
     */
    private final List<String> includes_ = new ArrayList<>();
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
    private @Nullable File csv_;
    /**
     * The file to write execution data to.
     */
    private @Nullable File destFile_;
    /**
     * Whether to skip generating the CSV report.
     */
    private boolean disableCsv_;
    /**
     * Whether to skip generating the HTML report.
     */
    private boolean disableHtml_;
    /**
     * Whether to skip generating the XML report.
     */
    private boolean disableXml_;
    /**
     * The source file encoding.
     * <p>
     * {@code null} means no encoding was specified; the platform default encoding will be used.
     */
    private @Nullable String encoding_;
    /**
     * The directory of the HTML report output.
     */
    private @Nullable File htmlDirectory_;
    /**
     * Cached PathMatcher, built lazily on first use and invalidated when include/exclude patterns change.
     */
    private @Nullable PathMatcher pathMatcher_;
    /**
     * The project reference.
     */
    private @Nullable BaseProject project_;
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
    private @Nullable TestOperation<?, ?> testOperation_;
    /**
     * The location of the XML report.
     */
    private @Nullable File xml_;

    /**
     * Performs the operation execution that can be wrapped by the {@code #executeOnce} call.
     *
     * @throws NullPointerException if the project is {@code null}
     */
    @Override
    @SuppressFBWarnings("CC_CYCLOMATIC_COMPLEXITY")
    public void execute() throws Exception {
        ObjectTools.requireNonNull(project_, "project");

        var buildJacocoReportsDir = IOTools.resolveFile(
                project_.buildDirectory(),
                "reports",
                "jacoco",
                "test");
        var buildJacocoExecDir = IOTools.resolveFile(project_.buildDirectory(), "jacoco");
        var buildJacocoExec = IOTools.resolveFile(buildJacocoExecDir, "jacoco.exec");

        // Resolve destFile locally to avoid mutating instance state
        var effectiveDestFile = (destFile_ != null) ? destFile_ : buildJacocoExec;

        // Resolve exec files locally to avoid mutating execFiles_ as a side effect
        var effectiveExecFiles = new ArrayList<>(execFiles_);

        // Run tests unless execFiles() was explicitly called AND no testOperation was set
        boolean shouldRunTests = testOperation_ != null || execFiles_.isEmpty();

        if (shouldRunTests) {
            var testOp = (testOperation_ != null)
                    ? testOperation_
                    : project_.testOperation().fromProject(project_);

            var agentRuntime = project_.extensionClasspathJars(
                    new Dependency("org.jacoco", "org.jacoco.agent").withClassifier("runtime"));

            testOp.javaOptions().javaAgent(agentRuntime.get(0), "destfile=" + effectiveDestFile.getPath());

            if (!testToolOptions_.isEmpty()) {
                testOp.testToolOptions().addAll(testToolOptions_);
            }

            testOp.execute();

            if (logger.isLoggable(Level.INFO) && !silent()) {
                logger.log(Level.INFO, "Execution Data: " + effectiveDestFile);
            }

            // Merge the fresh exec with any pre-existing ones
            if (effectiveDestFile.exists() && !effectiveExecFiles.contains(effectiveDestFile)) {
                effectiveExecFiles.add(effectiveDestFile);
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

        var effectiveHtmlDir = disableHtml_
                ? null
                : (htmlDirectory_ != null)
                ? htmlDirectory_
                : new File(buildJacocoReportsDir, HTML);

        var effectiveXml = disableXml_
                ? null
                : (xml_ != null)
                ? xml_
                : new File(buildJacocoReportsDir, "jacocoTestReport.xml");

        var effectiveCsv = disableCsv_
                ? null
                : (csv_ != null)
                ? csv_
                : new File(buildJacocoReportsDir, "jacocoTestReport.csv");

        // Check if all reports are disabled BEFORE creating directories
        if (disableCsv_ && disableHtml_ && disableXml_) {
            if (logger.isLoggable(Level.WARNING) && !silent()) {
                logger.warning("All report formats are disabled; skipping analysis.");
            }
            return;
        }

        try {
            IOTools.createDirs(buildJacocoReportsDir);
        } catch (IOException | SecurityException e) {
            throw new IOException(
                    "Could not create JaCoCo reports directory: " + buildJacocoReportsDir.getAbsolutePath(),
                    e);
        }

        try {
            IOTools.createDirs(buildJacocoExecDir);
        } catch (IOException | SecurityException e) {
            throw new IOException(
                    "Could not create JaCoCo exec directory: " + buildJacocoExecDir.getAbsolutePath(),
                    e);
        }

        var loader = loadExecFiles(effectiveExecFiles);

        // Warn if no execution data was actually loaded
        if (loader.getExecutionDataStore().getContents().isEmpty() && logger.isLoggable(Level.WARNING) && !silent()) {
            logger.warning("Report will be empty. No coverage data found from exec files: " + effectiveExecFiles);
        }

        var bundle = analyze(loader.getExecutionDataStore(), effectiveClassFiles);

        long classCount = bundle.getClassCounter().getTotalCount();

        if (logger.isLoggable(Level.INFO) && !silent()) {
            logger.log(Level.INFO, "Analyzing " + classCount + " classes");
        }
        if (classCount == 0 && logger.isLoggable(Level.WARNING) && !silent()) {
            logger.warning("No classes matched include/exclude patterns or no class files found.");
        }

        writeReports(bundle, loader, effectiveXml, effectiveCsv, effectiveHtmlDir, effectiveSourceFiles);
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @throws NullPointerException     if {@code classFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code classFiles} is empty
     * @see #classFiles(Collection)
     */
    public JacocoReportOperation classFiles(File... classFiles) {
        ObjectTools.requireNotEmpty(classFiles, CLASS_FILES);
        classFiles_.addAll(List.of(classFiles));
        return this;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @throws NullPointerException     if {@code classFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code classFiles} is empty, or contains blank elements
     * @see #classFilesStrings(Collection)
     */
    public JacocoReportOperation classFiles(String... classFiles) {
        TextTools.requireNotBlank(CLASS_FILES, classFiles);
        classFiles_.addAll(CollectionTools.combineStringsToFiles(classFiles));
        return this;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @throws NullPointerException     if {@code classFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code classFiles} is empty
     * @see #classFilesPaths(Collection)
     */
    public JacocoReportOperation classFiles(Path... classFiles) {
        ObjectTools.requireNotEmpty(classFiles, CLASS_FILES);
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
    public List<File> classFiles() {
        return classFiles_;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @throws NullPointerException     if {@code classFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code classFiles} is empty
     * @see #classFiles(File...)
     */
    public final JacocoReportOperation classFiles(Collection<File> classFiles) {
        ObjectTools.requireNotEmpty(classFiles, CLASS_FILES);
        classFiles_.addAll(classFiles);
        return this;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @throws NullPointerException     if {@code classFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code classFiles} is empty
     * @see #classFiles(Path...)
     */
    public final JacocoReportOperation classFilesPaths(Collection<Path> classFiles) {
        ObjectTools.requireNotEmpty(classFiles, CLASS_FILES);
        classFiles_.addAll(CollectionTools.combinePathsToFiles(classFiles));
        return this;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @throws NullPointerException     if {@code classFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code classFiles} is empty, or contains blank elements
     * @see #classFiles(String...)
     */
    public final JacocoReportOperation classFilesStrings(Collection<String> classFiles) {
        TextTools.requireNotBlank(classFiles, CLASS_FILES);
        classFiles_.addAll(CollectionTools.combineStringsToFiles(classFiles));
        return this;
    }

    /**
     * Sets the location of the CSV report.
     *
     * @param csv the report location
     * @return this operation instance
     * @throws NullPointerException if {@code csv} is {@code null}
     */
    public JacocoReportOperation csv(File csv) {
        csv_ = ObjectTools.requireNonNull(csv, "csv");

        return this;
    }

    /**
     * Sets the location of the CSV report.
     *
     * @param csv the report location
     * @return this operation instance
     * @throws NullPointerException     if {@code csv} is {@code null}
     * @throws IllegalArgumentException if {@code csv} is blank
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JacocoReportOperation csv(String csv) {
        TextTools.requireNotBlank(csv, "csv");
        return csv(new File(csv));
    }

    /**
     * Sets the location of the CSV report.
     *
     * @param csv the report location
     * @return this operation instance
     * @throws NullPointerException if {@code csv} is {@code null}
     */
    public JacocoReportOperation csv(Path csv) {
        ObjectTools.requireNonNull(csv, "csv");
        return csv(csv.toFile());
    }

    /**
     * Returns the CSV report location.
     *
     * @return the CSV report location
     */
    @Nullable
    public File csv() {
        return csv_;
    }

    /**
     * Sets the file to write execution data to.
     *
     * @param destFile the file
     * @return this operation instance
     * @throws NullPointerException if {@code destFile} is {@code null}
     */
    public JacocoReportOperation destFile(File destFile) {
        destFile_ = ObjectTools.requireNonNull(destFile, "destFile");
        return this;
    }

    /**
     * Sets the file to write execution data to.
     *
     * @param destFile the file
     * @return this operation instance
     * @throws NullPointerException     if {@code destFile} is {@code null}
     * @throws IllegalArgumentException if {@code destFile} is blank
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JacocoReportOperation destFile(String destFile) {
        TextTools.requireNotBlank(destFile, "destFile");
        return destFile(new File(destFile));
    }

    /**
     * Sets the file to write execution data to.
     *
     * @param destFile the file
     * @return this operation instance
     * @throws NullPointerException if {@code destFile} is {@code null}
     */
    public JacocoReportOperation destFile(Path destFile) {
        ObjectTools.requireNonNull(destFile, "destFile");
        return destFile(destFile.toFile());
    }

    /**
     * Returns the file to write execution data to.
     *
     * @return the file to write execution data to
     */
    @Nullable
    public File destFile() {
        return destFile_;
    }

    /**
     * Disables generation of the CSV report.
     *
     * @return this operation
     * @since 1.1
     */
    public JacocoReportOperation disableCsv() {
        disableCsv_ = true;
        return this;
    }

    /**
     * Disables generation of the HTML report.
     *
     * @return this operation instance
     * @since 1.1
     */
    public JacocoReportOperation disableHtml() {
        disableHtml_ = true;
        return this;
    }

    /**
     * Disables generation of the XML report.
     *
     * @return this operation instance
     * @since 1.1
     */
    public JacocoReportOperation disableXml() {
        disableXml_ = true;
        return this;
    }

    /**
     * Enables generation of all report formats (CSV, HTML, and XML).
     * <p>
     * This is a convenience method equivalent to calling
     * {@link #enableCsv()}, {@link #enableHtml()}, and {@link #enableXml()}.
     * All reports are enabled by default.
     * <p>
     * Example:
     * <pre>{@code
     * new JacocoReportOperation()
     *     .fromProject(project)
     *     .disableHtml()
     *     .disableCsv()
     *     .enableAllReports() // re-enables everything
     *     .execute();
     * }</pre>
     *
     * @return this operation instance for method chaining
     * @see #disableCsv()
     * @see #disableHtml()
     * @see #disableXml()
     * @since 1.1
     */
    public JacocoReportOperation enableAllReports() {
        disableCsv_ = false;
        disableHtml_ = false;
        disableXml_ = false;
        return this;
    }

    /**
     * Enables generation of the CSV report.
     * <p>
     * Call this to re-enable CSV output after {@link #disableCsv()} was called.
     * CSV generation is enabled by default.
     *
     * @return this operation instance for method chaining
     * @see #disableCsv()
     * @see #isCsvDisabled()
     * @since 1.1
     */
    public JacocoReportOperation enableCsv() {
        disableCsv_ = false;
        return this;
    }

    /**
     * Enables generation of the HTML report.
     * <p>
     * Call this to re-enable HTML output after {@link #disableHtml()} was called.
     * HTML generation is enabled by default. The HTML report includes source
     * highlighting and is suitable for human review.
     *
     * @return this operation instance for method chaining
     * @see #disableHtml()
     * @see #isHtmlDisabled()
     * @since 1.1
     */
    public JacocoReportOperation enableHtml() {
        disableHtml_ = false;
        return this;
    }

    /**
     * Enables generation of the XML report.
     * <p>
     * Call this to re-enable XML output after {@link #disableXml()} was called.
     * XML generation is enabled by default. The XML report is machine-readable
     * and commonly used by CI tools like SonarQube.
     *
     * @return this operation instance for method chaining
     * @see #disableXml()
     * @see #isXmlDisabled()
     * @since 1.1
     */
    public JacocoReportOperation enableXml() {
        disableXml_ = false;
        return this;
    }

    /**
     * Sets the source file encoding. The platform encoding is used by default.
     *
     * @param encoding the encoding
     * @return this operation instance
     * @throws NullPointerException     if {@code encoding} is {@code null}
     * @throws IllegalArgumentException if {@code encoding} is blank
     */
    public JacocoReportOperation encoding(String encoding) {
        encoding_ = TextTools.requireNotBlank(encoding, "encoding");
        return this;
    }

    /**
     * Returns the source file encoding, or {@code null} if not set (platform default will be used).
     *
     * @return the source file encoding
     */
    @Nullable
    public String encoding() {
        return encoding_;
    }

    /**
     * Sets class name patterns to exclude from the coverage analysis.
     * <p>
     * Uses Ant-style path patterns matched against JVM internal class names with '/' separators.
     * Exclude patterns take precedence over include patterns.
     * <p>
     * Pattern syntax:
     * <ul>
     *   <li>{@code *} matches zero or more characters except '/'</li>
     *   <li>{@code **} matches zero or more directories</li>
     *   <li>{@code ?} matches a single character</li>
     * </ul>
     * <p>
     * Common examples:
     * <ul>
     *   <li>{@code "&ast;&ast;/&ast;Test&ast;"} - exclude all test classes</li>
     *   <li>{@code "&ast;&ast;/generated/&ast;&ast;"} - exclude generated code</li>
     *   <li>{@code "&ast;&ast;/proto/&ast;&ast;"} - exclude protobuf classes</li>
     *   <li>{@code "&ast;&ast;/Dagger&ast;"} - exclude Dagger generated classes</li>
     * </ul>
     *
     * @param patterns the Ant-style exclude patterns; must not be null or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if patterns is {@code null}, or contains {@code null} elements
     * @throws IllegalArgumentException if patterns is empty, or contains empty strings
     * @see #includes(String...)
     */
    public JacocoReportOperation excludes(String... patterns) {
        TextTools.requireNotBlank("patterns", patterns);
        excludes_.addAll(List.of(patterns));
        pathMatcher_ = null; // invalidate cached matcher
        return this;
    }

    /**
     * Returns the class name patterns to exclude from coverage analysis.
     * <p>
     * The returned list is the live internal list and is mutable by design
     * to support the fluent builder pattern.
     *
     * @return the exclude patterns, empty if no classes are excluded
     * @see #excludes(String...)
     */
    public List<String> excludes() {
        return excludes_;
    }

    /**
     * Sets class name patterns to exclude from the coverage analysis.
     * <p>
     * Uses Ant-style path patterns matched against JVM internal class names with '/' separators.
     * Exclude patterns take precedence over include patterns.
     * <p>
     * Pattern syntax:
     * <ul>
     *   <li>{@code *} matches zero or more characters except '/'</li>
     *   <li>{@code **} matches zero or more directories</li>
     *   <li>{@code ?} matches a single character</li>
     * </ul>
     * <p>
     * Common examples:
     * <ul>
     *   <li>{@code "&ast;&ast;/&ast;Test&ast;"} - exclude all test classes</li>
     *   <li>{@code "&ast;&ast;/generated/&ast;&ast;"} - exclude generated code</li>
     *   <li>{@code "&ast;&ast;/proto/&ast;&ast;"} - exclude protobuf classes</li>
     *   <li>{@code "&ast;&ast;/Dagger&ast;"} - exclude Dagger generated classes</li>
     * </ul>
     *
     * @param patterns the Ant-style exclude patterns; must not be null, empty, or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if patterns is {@code null}, or contains {@code null} elements
     * @throws IllegalArgumentException if patterns is empty, or contains blank elements
     * @see #excludes(String...)
     * @see #includes(Collection)
     */
    public final JacocoReportOperation excludes(Collection<String> patterns) {
        TextTools.requireNotBlank(patterns, "patterns");
        excludes_.addAll(patterns);
        pathMatcher_ = null; // invalidate cached matcher
        return this;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     * <p>
     * If exec files are provided and {@link #testOperation(TestOperation)} is not called,
     * tests will not be executed. Reports are generated from the provided files only.
     * <p>
     * If both exec files and a test operation are provided, tests run and the new
     * execution data is merged with the provided files.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @throws NullPointerException     if {@code execFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code execFiles} is empty
     * @see #execFiles(Collection)
     */
    public JacocoReportOperation execFiles(File... execFiles) {
        ObjectTools.requireNotEmpty(execFiles, EXEC_FILES);
        execFiles_.addAll(List.of(execFiles));
        return this;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     * <p>
     * If exec files are provided and {@link #testOperation(TestOperation)} is not called,
     * tests will not be executed. Reports are generated from the provided files only.
     * <p>
     * If both exec files and a test operation are provided, tests run and the new
     * execution data is merged with the provided files.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @throws NullPointerException     if {@code execFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code execFiles} is empty, or contains blank elements
     * @see #execFilesStrings(Collection)
     */
    public JacocoReportOperation execFiles(String... execFiles) {
        TextTools.requireNotBlank(EXEC_FILES, execFiles);
        execFiles_.addAll(CollectionTools.combineStringsToFiles(execFiles));
        return this;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     * <p>
     * If exec files are provided and {@link #testOperation(TestOperation)} is not called,
     * tests will not be executed. Reports are generated from the provided files only.
     * <p>
     * If both exec files and a test operation are provided, tests run and the new
     * execution data is merged with the provided files.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @throws NullPointerException     if {@code execFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code execFiles} is empty
     * @see #execFilesPaths(Collection)
     */
    public JacocoReportOperation execFiles(Path... execFiles) {
        ObjectTools.requireNotEmpty(execFiles, EXEC_FILES);
        execFiles_.addAll(CollectionTools.combinePathsToFiles(execFiles));
        return this;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     * <p>
     * If exec files are provided and {@link #testOperation(TestOperation)} is not called,
     * tests will not be executed. Reports are generated from the provided files only.
     * <p>
     * If both exec files and a test operation are provided, tests run and the new
     * execution data is merged with the provided files.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @throws NullPointerException     if {@code execFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code execFiles} is empty
     * @see #execFiles(File...)
     */
    public final JacocoReportOperation execFiles(Collection<File> execFiles) {
        ObjectTools.requireNotEmpty(execFiles, EXEC_FILES);
        execFiles_.addAll(execFiles);
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
    public List<File> execFiles() {
        return execFiles_;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     * <p>
     * If exec files are provided and {@link #testOperation(TestOperation)} is not called,
     * tests will not be executed. Reports are generated from the provided files only.
     * <p>
     * If both exec files and a test operation are provided, tests run and the new
     * execution data is merged with the provided files.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @throws NullPointerException     if {@code execFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code execFiles} is empty
     * @see #execFiles(Path...)
     */
    public final JacocoReportOperation execFilesPaths(Collection<Path> execFiles) {
        ObjectTools.requireNotEmpty(execFiles, EXEC_FILES);
        execFiles_.addAll(CollectionTools.combinePathsToFiles(execFiles));
        return this;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     * <p>
     * If exec files are provided and {@link #testOperation(TestOperation)} is not called,
     * tests will not be executed. Reports are generated from the provided files only.
     * <p>
     * If both exec files and a test operation are provided, tests run and the new
     * execution data is merged with the provided files.
     *
     * @param execFiles the exec files
     * @return this operation instance
     * @throws NullPointerException     if {@code execFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code execFiles} is empty, or contains blank elements
     * @see #execFiles(String...)
     */
    public final JacocoReportOperation execFilesStrings(Collection<String> execFiles) {
        TextTools.requireNotBlank(execFiles, EXEC_FILES);
        execFiles_.addAll(CollectionTools.combineStringsToFiles(execFiles));
        return this;
    }

    /**
     * Configure the operation from a {@link BaseProject}.
     *
     * @param project the project
     * @return this operation instance
     * @throws NullPointerException if {@code project} is {@code null}
     */
    public JacocoReportOperation fromProject(BaseProject project) {
        project_ = ObjectTools.requireNonNull(project, "fromProject");
        return this;
    }

    /**
     * Sets the directory for the HTML report output.
     *
     * @param html the HTML report directory
     * @return this operation instance
     * @throws NullPointerException if {@code html} is {@code null}
     */
    public JacocoReportOperation html(File html) {
        htmlDirectory_ = ObjectTools.requireNonNull(html, HTML);
        return this;
    }

    /**
     * Sets the directory for the HTML report output.
     *
     * @param html the HTML report directory
     * @return this operation instance
     * @throws NullPointerException     if {@code html} is {@code null}
     * @throws IllegalArgumentException if {@code html} is blank
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JacocoReportOperation html(String html) {
        TextTools.requireNotBlank(html, HTML);
        return html(new File(html));
    }

    /**
     * Sets the directory for the HTML report output.
     *
     * @param html the HTML report directory
     * @return this operation instance
     * @throws NullPointerException if {@code html} is {@code null}
     */
    public JacocoReportOperation html(Path html) {
        ObjectTools.requireNonNull(html, HTML);
        return html(html.toFile());
    }

    /**
     * Returns the HTML report output directory.
     *
     * @return the HTML report directory
     */
    @Nullable
    public File html() {
        return htmlDirectory_;
    }

    /**
     * Sets class name patterns to include in the coverage analysis.
     * <p>
     * Uses Ant-style path patterns matched against JVM internal class names with '/' separators.
     * If no includes are specified, all classes are included by default unless excluded.
     * <p>
     * Pattern syntax:
     * <ul>
     *   <li>{@code *} matches zero or more characters except '/'</li>
     *   <li>{@code **} matches zero or more directories</li>
     *   <li>{@code ?} matches a single character</li>
     * </ul>
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code "com/myapp/&ast;&ast;"} - all classes in com.myapp and subpackages</li>
     *   <li>{@code "&ast;&ast;/service/&ast;"} - all classes directly in any service package</li>
     *   <li>{@code "com/myapp/MyClass"} - single class com.myapp.MyClass</li>
     * </ul>
     *
     * @param patterns the Ant-style include patterns; must not be null or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if patterns is {@code null}, or contains {@code null} elements
     * @throws IllegalArgumentException if patterns is empty, or contains empty elements
     * @see #excludes(String...)
     */
    public JacocoReportOperation includes(String... patterns) {
        TextTools.requireNotBlank("includes", patterns);
        includes_.addAll(List.of(patterns));
        pathMatcher_ = null; // invalidate cached matcher
        return this;
    }

    /**
     * Returns the class name patterns to include in coverage analysis.
     * <p>
     * The returned list is the live internal list and is mutable by design
     * to support the fluent builder pattern.
     *
     * @return the include patterns, empty if all classes are included
     * @see #includes(String...)
     */
    public List<String> includes() {
        return includes_;
    }

    /**
     * Sets class name patterns to include in the coverage analysis.
     * <p>
     * Uses Ant-style path patterns matched against JVM internal class names with '/' separators.
     * If no includes are specified, all classes are included by default unless excluded.
     * <p>
     * Pattern syntax:
     * <ul>
     *   <li>{@code *} matches zero or more characters except '/'</li>
     *   <li>{@code **} matches zero or more directories</li>
     *   <li>{@code ?} matches a single character</li>
     * </ul>
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code "com/myapp/&ast;&ast;"} - all classes in com.myapp and subpackages</li>
     *   <li>{@code "&ast;&ast;/service/&ast;"} - all classes directly in any service package</li>
     *   <li>{@code "com/myapp/MyClass"} - single class com.myapp.MyClass</li>
     * </ul>
     *
     * @param patterns the Ant-style include patterns; must not be null, empty, or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if patterns is {@code null}, or contains {@code null} elements
     * @throws IllegalArgumentException if patterns is empty, or contains blank elements
     * @see #includes(String...)
     * @see #excludes(Collection)
     */
    public final JacocoReportOperation includes(Collection<String> patterns) {
        TextTools.requireNotBlank(patterns, "includes");
        includes_.addAll(patterns);
        pathMatcher_ = null; // invalidate cached matcher
        return this;
    }

    /**
     * Returns whether CSV report generation is disabled.
     *
     * @return {@code true} if the CSV report is disabled
     * @since 1.1
     */
    public boolean isCsvDisabled() {
        return disableCsv_;
    }

    /**
     * Returns whether HTML report generation is disabled.
     *
     * @return {@code true} if the HTML report is disabled
     * @since 1.1
     */
    public boolean isHtmlDisabled() {
        return disableHtml_;
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
     * Returns whether XML report generation is disabled.
     *
     * @return {@code true} if the XML report is disabled
     * @since 1.1
     */
    public boolean isXmlDisabled() {
        return disableXml_;
    }

    /**
     * Sets the name used for the report.
     *
     * @param name the name
     * @return this operation instance
     * @throws NullPointerException     if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public JacocoReportOperation name(String name) {
        reportName_ = TextTools.requireNotBlank(name, "name");
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
     * @throws NullPointerException     if {@code sourceFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code sourceFiles} is empty
     * @see #sourceFiles(Collection)
     */
    public JacocoReportOperation sourceFiles(File... sourceFiles) {
        ObjectTools.requireNotEmpty(sourceFiles, SOURCE_FILES);
        sourceFiles_.addAll(List.of(sourceFiles));
        return this;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @throws NullPointerException     if {@code sourceFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code sourceFiles} is empty, or contains blank elements
     * @see #sourceFilesStrings(Collection)
     */
    public JacocoReportOperation sourceFiles(String... sourceFiles) {
        TextTools.requireNotBlank(SOURCE_FILES, sourceFiles);
        sourceFiles_.addAll(CollectionTools.combineStringsToFiles(sourceFiles));
        return this;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @throws NullPointerException     if {@code sourceFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code sourceFiles} is empty
     * @see #sourceFilesPaths(Collection)
     */
    public JacocoReportOperation sourceFiles(Path... sourceFiles) {
        ObjectTools.requireNotEmpty(sourceFiles, SOURCE_FILES);
        sourceFiles_.addAll(CollectionTools.combinePathsToFiles(sourceFiles));
        return this;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @throws NullPointerException     if {@code sourceFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code sourceFiles} is empty
     * @see #sourceFiles(File...)
     */
    public final JacocoReportOperation sourceFiles(Collection<File> sourceFiles) {
        ObjectTools.requireNotEmpty(sourceFiles, SOURCE_FILES);
        sourceFiles_.addAll(sourceFiles);
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
    public List<File> sourceFiles() {
        return sourceFiles_;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @throws NullPointerException     if {@code sourceFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code sourceFiles} is empty
     * @see #sourceFiles(Path...)
     */
    public final JacocoReportOperation sourceFilesPaths(Collection<Path> sourceFiles) {
        ObjectTools.requireNotEmpty(sourceFiles, SOURCE_FILES);
        sourceFiles_.addAll(CollectionTools.combinePathsToFiles(sourceFiles));
        return this;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @throws NullPointerException     if {@code sourceFiles} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code sourceFiles} is empty, or contains blank elements
     * @see #sourceFiles(String...)
     */
    public final JacocoReportOperation sourceFilesStrings(Collection<String> sourceFiles) {
        TextTools.requireNotBlank(sourceFiles, SOURCE_FILES);
        sourceFiles_.addAll(CollectionTools.combineStringsToFiles(sourceFiles));
        return this;
    }

    /**
     * Sets the tab stop width for the source pages. Default is {@code 4}.
     *
     * @param tabWidth the tab width
     * @return this operation instance
     * @throws IllegalArgumentException if {@code tabWidth} is not positive
     */
    public JacocoReportOperation tabWidth(int tabWidth) {
        tabWidth_ = ObjectTools.requirePositive(tabWidth, "tabWidth");
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
     * <p>
     * If set, tests are always executed even when {@link #execFiles(File...)} is also called.
     * The JaCoCo agent is attached to this operation and the resulting execution data is
     * merged with any provided exec files.
     * <p>
     * If not set and no exec files are provided, {@link #fromProject(BaseProject)} will
     * create a default test operation from the project.
     *
     * @param testOperation the test operation; must not be null
     * @return this operation instance
     * @throws NullPointerException if {@code testOperation} is {@code null}
     * @see #execFiles(File...)
     */
    public JacocoReportOperation testOperation(TestOperation<?, ?> testOperation) {
        testOperation_ = ObjectTools.requireNonNull(testOperation, "Test operation must not be null");
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
    public List<String> testToolOptions() {
        return testToolOptions_;
    }

    /**
     * Sets the test tool options.
     *
     * @param options the options to set
     * @return this operation instance
     * @throws NullPointerException     if {@code options} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code options} is empty, or contains blank elements
     * @see #testToolOptions(Collection)
     */
    public JacocoReportOperation testToolOptions(String... options) {
        TextTools.requireNotBlank("testToolOptions", options);
        testToolOptions_.addAll(List.of(options));
        return this;
    }

    /**
     * Sets the test tool options.
     *
     * @param options the options to set
     * @return this operation instance
     * @throws NullPointerException     if {@code options} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code options} is empty, or contains blank elements
     * @see #testToolOptions(String...)
     */
    public final JacocoReportOperation testToolOptions(Collection<String> options) {
        TextTools.requireNotBlank(options, "Test tool options must not be null or empty");
        testToolOptions_.addAll(options);
        return this;
    }

    /**
     * Sets the location of the XML report.
     *
     * @param xml the report location
     * @return this operation instance
     * @throws NullPointerException if {@code xml} is {@code null}
     */
    public JacocoReportOperation xml(File xml) {
        xml_ = ObjectTools.requireNonNull(xml, "xml");
        return this;
    }

    /**
     * Sets the location of the XML report.
     *
     * @param xml the report location
     * @return this operation instance
     * @throws NullPointerException     if {@code xml} is {@code null}
     * @throws IllegalArgumentException if {@code xml} is blank
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JacocoReportOperation xml(String xml) {
        TextTools.requireNotBlank(xml, "xml");
        return xml(new File(xml));
    }

    /**
     * Sets the location of the XML report.
     *
     * @param xml the report location
     * @return this operation instance
     * @throws NullPointerException if {@code xml} is {@code null}
     */
    public JacocoReportOperation xml(Path xml) {
        ObjectTools.requireNonNull(xml, "xml");
        return xml(xml.toFile());
    }

    /**
     * Returns the XML report location.
     *
     * @return the XML report location
     */
    @Nullable
    public File xml() {
        return xml_;
    }

    private IBundleCoverage analyze(ExecutionDataStore data, List<File> classFiles) throws IOException {
        var matcher = pathMatcher();

        var builder = new CoverageBuilder() {
            @Override
            public void visitCoverage(IClassCoverage coverage) {
                if (matcher.matches(coverage.getName())) {
                    super.visitCoverage(coverage);
                }
            }
        };

        var analyzer = new Analyzer(data, builder);

        for (var classFile : classFiles) {
            if (!classFile.exists()) {
                if (logger.isLoggable(Level.WARNING) && !silent()) {
                    logger.warning("Class file not found, skipping: " + classFile.getAbsolutePath());
                }
                continue;
            }
            analyzer.analyzeAll(classFile);
        }

        var bundle = builder.getBundle(reportName_);

        // IBundleCoverage has getPackages(), not getClasses()
        if (logger.isLoggable(Level.WARNING) && !silent()
                && (!includes_.isEmpty() || !excludes_.isEmpty())
                && bundle.getPackages().isEmpty()) {
            logger.warning("No classes matched include/exclude patterns. "
                    + "Use '/' separators for JVM class names, e.g. 'com/example/**'");
        }

        return bundle;
    }

    private ExecFileLoader loadExecFiles(List<File> execFiles) throws IOException {
        var loader = new ExecFileLoader();
        if (execFiles.isEmpty() && logger.isLoggable(Level.WARNING) && !silent()) {
            logger.warning("No execution data files provided.");
        } else {
            for (var f : execFiles) {
                if (!f.exists()) {
                    if (logger.isLoggable(Level.WARNING) && !silent()) {
                        logger.warning("Exec file not found, skipping: " + f.getAbsolutePath());
                    }
                    continue;
                }
                if (logger.isLoggable(Level.INFO) && !silent()) {
                    logger.log(Level.INFO, "Loading execution data: " + f.getAbsolutePath());
                }
                loader.load(f);
            }
        }
        return loader;
    }

    /**
     * Returns the cached {@link PathMatcher}, building it lazily on first use.
     * The cache is invalidated (set to {@code null}) whenever {@link #includes_} or
     * {@link #excludes_} are modified via the fluent setters.
     */
    private PathMatcher pathMatcher() {
        if (pathMatcher_ == null) {
            pathMatcher_ = PathMatcher.of(includes_, excludes_);
        }
        return pathMatcher_;
    }

    @SuppressWarnings({"PMD.CloseResource", "PMD.AvoidCatchingGenericException"})
    private IReportVisitor reportVisitor(@Nullable File xml, @Nullable File csv, @Nullable File htmlDirectory)
            throws IOException {
        OutputStream xmlStream = null;
        OutputStream csvStream = null;
        try {
            var visitors = new ArrayList<IReportVisitor>();

            if (xml != null && !disableXml_) {
                xmlStream = Files.newOutputStream(xml.toPath());
                visitors.add(new XMLFormatter().createVisitor(xmlStream));
            }

            if (csv != null && !disableCsv_) {
                csvStream = Files.newOutputStream(csv.toPath());
                visitors.add(new CSVFormatter().createVisitor(csvStream));
            }

            if (!disableHtml_) {
                visitors.add(new HTMLFormatter().createVisitor(new FileMultiReportOutput(htmlDirectory)));
            }

            return new MultiReportVisitor(visitors);
        } catch (Throwable t) {
            if (xmlStream != null) {
                try {
                    xmlStream.close();
                } catch (IOException ignored) {
                }
            }
            if (csvStream != null) {
                try {
                    csvStream.close();
                } catch (IOException ignored) {
                }
            }
            throw t;
        }
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private ISourceFileLocator sourceLocator(Collection<File> sourceFiles) {
        var multi = new MultiSourceFileLocator(tabWidth_);
        for (var f : sourceFiles) {
            multi.add(new DirectorySourceFileLocator(f, encoding_, tabWidth_));
        }
        return multi;
    }

    private void writeReports(IBundleCoverage bundle,
                              ExecFileLoader loader,
                              @Nullable File xml,
                              @Nullable File csv,
                              @Nullable File htmlDirectory,
                              List<File> sourceFiles)
            throws IOException {
        var visitor = reportVisitor(xml, csv, htmlDirectory);
        visitor.visitInfo(loader.getSessionInfoStore().getInfos(), loader.getExecutionDataStore().getContents());
        try {
            visitor.visitBundle(bundle, sourceLocator(sourceFiles));
        } finally {
            visitor.visitEnd(); // always flushes/closes
        }
        if (logger.isLoggable(Level.INFO) && !silent()) {
            if (xml != null && !disableXml_) {
                logger.log(Level.INFO, "XML Report: " + xml.toURI());
            }
            if (csv != null && !disableCsv_) {
                logger.log(Level.INFO, "CSV Report: " + csv.toURI());
            }
            if (!disableHtml_) {
                logger.log(Level.INFO, "HTML Report: " + new File(htmlDirectory, "index.html").toURI());
            }
        }
    }

    /**
     * Ant-style path matcher for include/exclude patterns.
     * <p>
     * Converts Ant patterns to regex: {@code **} -> {@code .*}, {@code *} -> {@code [^/]*}, {@code ?} -> {@code [^/]}
     */
    private record PathMatcher(List<Pattern> includePatterns_, List<Pattern> excludePatterns_) {

        private static List<Pattern> compilePatterns(Collection<String> globs) {
            return globs.stream().map(PathMatcher::globToRegex).map(Pattern::compile).toList();
        }

        private static String globToRegex(String glob) {
            var normalized = glob.replace('\\', '/');
            var sb = new StringBuilder("^");

            int i = 0;
            while (i < normalized.length()) {
                char c = normalized.charAt(i);
                switch (c) {
                    case '*' -> {
                        if (i + 1 < normalized.length() && normalized.charAt(i + 1) == '*') {
                            sb.append(".*"); // ** matches any characters including '/'
                            i += 2; // consume both '*' characters
                        } else {
                            sb.append("[^/]*"); // * matches any characters except '/'
                            i++;
                        }
                    }
                    case '?' -> {
                        sb.append("[^/]"); // ? matches a single character except '/'
                        i++;
                    }
                    case '.', '{', '}', '(', ')', '+', '^', '$', '|', '\\' -> {
                        sb.append('\\').append(c); // escape regex specials
                        i++;
                    }
                    default -> {
                        sb.append(c);
                        i++;
                    }
                }
            }
            return sb.append('$').toString();
        }

        static PathMatcher of(Collection<String> includes, Collection<String> excludes) {
            return new PathMatcher(compilePatterns(includes), compilePatterns(excludes));
        }

        boolean matches(String className) {
            var normalized = className.replace('\\', '/');

            boolean included = includePatterns_.isEmpty()
                    || includePatterns_.stream().anyMatch(p -> p.matcher(normalized).matches());

            return included && excludePatterns_.stream().noneMatch(p -> p.matcher(normalized).matches());
        }
    }
}