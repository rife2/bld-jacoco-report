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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jacoco.core.JaCoCo;
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
import rife.bld.BaseProject;
import rife.bld.extension.tools.CollectionTools;
import rife.bld.extension.tools.IOTools;
import rife.bld.extension.tools.ObjectTools;
import rife.bld.operations.AbstractOperation;
import rife.bld.operations.TestOperation;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
 *
 * @author <a href="https://erik.thauvin.net/">Erik C. Thauvin</a>
 * @since 1.0
 */

@SuppressWarnings("PMD.ExcessiveImports")
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "intentional and documented")
public class JacocoReportOperation extends AbstractOperation<JacocoReportOperation> {

    public static final String CLASS_FILE_INVALID = "Class files values must not be null";
    public static final String EXEC_FILES_NOT_VALID = "Exec files values must not be null";
    public static final String SOURCE_FILE_NOT_VALID = "Source files values must not be null";
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
    public void execute() throws Exception {
        Objects.requireNonNull(project_, "A project must be specified");

        var buildJacocoReportsDir = Path.of(project_.buildDirectory().getPath(), "reports", "jacoco", "test").toFile();
        var buildJacocoExecDir = Path.of(project_.buildDirectory().getPath(), "jacoco").toFile();
        var buildJacocoExec = Path.of(buildJacocoExecDir.getPath(), "jacoco.exec").toFile();

        // Resolve destFile locally to avoid mutating instance state
        var effectiveDestFile = (destFile_ != null) ? destFile_ : buildJacocoExec;

        // Resolve exec files locally to avoid mutating execFiles_ as a side effect
        List<File> effectiveExecFiles = new ArrayList<>(execFiles_);

        // Run tests unless execFiles() was explicitly called AND no testOperation was set
        boolean shouldRunTests = testOperation_ != null || execFiles_.isEmpty();

        if (shouldRunTests) {
            var testOp = (testOperation_ != null) ? testOperation_ : project_.testOperation().fromProject(project_);

            var javaAgent = Path.of(project_.libBldDirectory().getPath(), "org.jacoco.agent-" + getJacocoAgentVersion() + "-runtime.jar").toFile();

            if (!javaAgent.exists()) {
                if (logger.isLoggable(Level.SEVERE) && !silent()) {
                    logger.severe("JaCoCo agent does not exist: " + javaAgent);
                }
                throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
            }

            testOp.javaOptions().javaAgent(javaAgent, "destfile=" + effectiveDestFile.getPath());

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
        var effectiveSourceFiles = sourceFiles_.isEmpty() ? List.of(project_.srcMainJavaDirectory()) : List.copyOf(sourceFiles_);

        // Resolve class files locally
        var effectiveClassFiles = classFiles_.isEmpty() ? List.of(project_.buildMainDirectory()) : List.copyOf(classFiles_);

        var effectiveHtmlDir = (htmlDirectory_ != null) ? htmlDirectory_ : new File(buildJacocoReportsDir, "html");

        var effectiveXml = (xml_ != null) ? xml_ : new File(buildJacocoReportsDir, "jacocoTestReport.xml");

        var effectiveCsv = (csv_ != null) ? csv_ : new File(buildJacocoReportsDir, "jacocoTestReport.csv");

        if (!IOTools.mkdirs(buildJacocoReportsDir)) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.severe("Could not create reports directory: " + buildJacocoReportsDir.getAbsolutePath());
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
        if (!IOTools.mkdirs(buildJacocoExecDir)) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.severe("Could not create directory: " + buildJacocoExecDir.getAbsolutePath());
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
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
     * @see #classFiles(Collection)
     */
    public JacocoReportOperation classFiles(@NonNull File... classFiles) {
        ObjectTools.requireAllNotEmpty(classFiles, CLASS_FILE_INVALID);
        classFiles_.addAll(List.of(classFiles));
        return this;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @see #classFilesStrings(Collection)
     */
    public JacocoReportOperation classFiles(@NonNull String... classFiles) {
        ObjectTools.requireAllNotEmpty(classFiles, "Class files values must not be null or empty");
        classFiles_.addAll(CollectionTools.combineStringsToFiles(classFiles));
        return this;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     * @see #classFilesPaths(Collection)
     */
    public JacocoReportOperation classFiles(@NonNull Path... classFiles) {
        ObjectTools.requireAllNotEmpty(classFiles, CLASS_FILE_INVALID);
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
     * @see #classFiles(File...)
     */
    public final JacocoReportOperation classFiles(@NonNull Collection<File> classFiles) {
        ObjectTools.requireAllNotEmpty(classFiles, CLASS_FILE_INVALID);
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
    public final JacocoReportOperation classFilesPaths(@NonNull Collection<Path> classFiles) {
        ObjectTools.requireAllNotEmpty(classFiles, CLASS_FILE_INVALID);
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
    public final JacocoReportOperation classFilesStrings(@NonNull Collection<String> classFiles) {
        ObjectTools.requireAllNotEmpty(classFiles, "Class files values must not be null or empty");
        classFiles_.addAll(CollectionTools.combineStringsToFiles(classFiles));
        return this;
    }

    /**
     * Sets the location of the CSV report.
     *
     * @param csv the report location
     * @return this operation instance
     */
    public JacocoReportOperation csv(@NonNull File csv) {
        csv_ = Objects.requireNonNull(csv, "The csv file must not be null");

        return this;
    }

    /**
     * Sets the location of the CSV report.
     *
     * @param csv the report location
     * @return this operation instance
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JacocoReportOperation csv(@NonNull String csv) {
        ObjectTools.requireNotEmpty(csv, "The csv file must not be null or empty");
        return csv(new File(csv));
    }

    /**
     * Sets the location of the CSV report.
     *
     * @param csv the report location
     * @return this operation instance
     */
    public JacocoReportOperation csv(@NonNull Path csv) {
        Objects.requireNonNull(csv, "The csv file must not be null");
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
    public JacocoReportOperation destFile(@NonNull File destFile) {
        destFile_ = Objects.requireNonNull(destFile, "Destination file must not be null");
        return this;
    }

    /**
     * Sets the file to write execution data to.
     *
     * @param destFile the file
     * @return this operation instance
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JacocoReportOperation destFile(@NonNull String destFile) {
        ObjectTools.requireNotEmpty(destFile, "Destination file must not be null or empty");
        return destFile(new File(destFile));
    }

    /**
     * Sets the file to write execution data to.
     *
     * @param destFile the file
     * @return this operation instance
     */
    public JacocoReportOperation destFile(@NonNull Path destFile) {
        Objects.requireNonNull(destFile, "The dest file must not be null");
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
    public JacocoReportOperation encoding(@NonNull String encoding) {
        encoding_ = Objects.requireNonNull(encoding, "The encoding must not be null");
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
     * @throws NullPointerException     if patterns is null or contains null
     * @throws IllegalArgumentException if patterns contains empty strings
     * @see #includes(String...)
     */
    public JacocoReportOperation excludes(@NonNull String... patterns) {
        ObjectTools.requireAllNotEmpty(patterns, "Exclude patterns must not be null or empty");
        excludes_.addAll(List.of(patterns));
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
     * @throws NullPointerException     if patterns is null or contains null
     * @throws IllegalArgumentException if patterns is empty or contains empty strings
     * @see #excludes(String...)
     * @see #includes(Collection)
     */
    public final JacocoReportOperation excludes(@NonNull Collection<String> patterns) {
        ObjectTools.requireAllNotEmpty(patterns, "Exclude patterns must not be null or empty");
        excludes_.addAll(patterns);
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
     * @see #execFiles(Collection)
     */
    public JacocoReportOperation execFiles(@NonNull File... execFiles) {
        ObjectTools.requireAllNotEmpty(execFiles, EXEC_FILES_NOT_VALID);
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
     * @see #execFilesStrings(Collection)
     */
    public JacocoReportOperation execFiles(@NonNull String... execFiles) {
        ObjectTools.requireAllNotEmpty(execFiles, "Exec files values must not be null or empty");
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
     * @see #execFilesPaths(Collection)
     */
    public JacocoReportOperation execFiles(@NonNull Path... execFiles) {
        ObjectTools.requireAllNotEmpty(execFiles, EXEC_FILES_NOT_VALID);
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
     * @see #execFiles(File...)
     */
    public final JacocoReportOperation execFiles(@NonNull Collection<File> execFiles) {
        ObjectTools.requireAllNotEmpty(execFiles, EXEC_FILES_NOT_VALID);
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
     * @see #execFiles(Path...)
     */
    public final JacocoReportOperation execFilesPaths(@NonNull Collection<Path> execFiles) {
        ObjectTools.requireAllNotEmpty(execFiles, EXEC_FILES_NOT_VALID);
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
     * @see #execFiles(String...)
     */
    public final JacocoReportOperation execFilesStrings(@NonNull Collection<String> execFiles) {
        ObjectTools.requireAllNotEmpty(execFiles, EXEC_FILES_NOT_VALID);
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
    public JacocoReportOperation html(@NonNull File html) {
        htmlDirectory_ = Objects.requireNonNull(html, "The html file must not be null");
        return this;
    }

    /**
     * Sets the directory for the HTML report output.
     *
     * @param html the HTML report directory
     * @return this operation instance
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JacocoReportOperation html(@NonNull String html) {
        ObjectTools.requireNotEmpty(html, "The html file must not be null or empty");
        return html(new File(html));
    }

    /**
     * Sets the directory for the HTML report output.
     *
     * @param html the HTML report directory
     * @return this operation instance
     */
    public JacocoReportOperation html(@NonNull Path html) {
        Objects.requireNonNull(html, "The html file must not be null");
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
     * @throws NullPointerException     if patterns is null or contains null
     * @throws IllegalArgumentException if patterns contains empty strings
     * @see #excludes(String...)
     */
    public JacocoReportOperation includes(@NonNull String... patterns) {
        ObjectTools.requireAllNotEmpty(patterns, "Include patterns must not be null or empty");
        includes_.addAll(List.of(patterns));
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
     * @throws NullPointerException     if patterns is null or contains null
     * @throws IllegalArgumentException if patterns is empty or contains empty strings
     * @see #includes(String...)
     * @see #excludes(Collection)
     */
    public final JacocoReportOperation includes(@NonNull Collection<String> patterns) {
        ObjectTools.requireAllNotEmpty(patterns, "Include patterns must not be null or empty");
        includes_.addAll(patterns);
        return this;
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
    public JacocoReportOperation name(@NonNull String name) {
        reportName_ = Objects.requireNonNull(name, "The name must not be null");
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
    public JacocoReportOperation sourceFiles(@NonNull File... sourceFiles) {
        ObjectTools.requireAllNotEmpty(sourceFiles, SOURCE_FILE_NOT_VALID);
        sourceFiles_.addAll(List.of(sourceFiles));
        return this;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @see #sourceFilesStrings(Collection)
     */
    public JacocoReportOperation sourceFiles(@NonNull String... sourceFiles) {
        ObjectTools.requireAllNotEmpty(sourceFiles, "Source files values must not be null or empty");
        sourceFiles_.addAll(CollectionTools.combineStringsToFiles(sourceFiles));
        return this;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     * @see #sourceFilesPaths(Collection)
     */
    public JacocoReportOperation sourceFiles(@NonNull Path... sourceFiles) {
        ObjectTools.requireAllNotEmpty(sourceFiles, SOURCE_FILE_NOT_VALID);
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
    public final JacocoReportOperation sourceFiles(@NonNull Collection<File> sourceFiles) {
        ObjectTools.requireAllNotEmpty(sourceFiles, SOURCE_FILE_NOT_VALID);
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
     * @see #sourceFiles(Path...)
     */
    public final JacocoReportOperation sourceFilesPaths(@NonNull Collection<Path> sourceFiles) {
        ObjectTools.requireAllNotEmpty(sourceFiles, SOURCE_FILE_NOT_VALID);
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
    public final JacocoReportOperation sourceFilesStrings(@NonNull Collection<String> sourceFiles) {
        ObjectTools.requireAllNotEmpty(sourceFiles, "Source files values must not be null or empty");
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
        if (tabWidth <= 0) {
            throw new IllegalArgumentException("tabWidth must be positive, got: " + tabWidth);
        }
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
     * @see #execFiles(File...)
     */
    public JacocoReportOperation testOperation(@NonNull TestOperation<?, ?> testOperation) {
        testOperation_ = Objects.requireNonNull(testOperation, "Test operation must not be null");
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
     * @see #testToolOptions(Collection)
     */
    public JacocoReportOperation testToolOptions(@NonNull String... options) {
        ObjectTools.requireAllNotEmpty(options, "Test tool options must not be null or empty");
        testToolOptions_.addAll(List.of(options));
        return this;
    }

    /**
     * Sets the test tool options.
     *
     * @param options the options to set
     * @return this operation instance
     * @see #testToolOptions(String...)
     */
    public final JacocoReportOperation testToolOptions(@NonNull Collection<String> options) {
        ObjectTools.requireAllNotEmpty(options, "Test tool options must not be null or empty");
        testToolOptions_.addAll(options);
        return this;
    }

    /**
     * Sets the location of the XML report.
     *
     * @param xml the report location
     * @return this operation instance
     */
    public JacocoReportOperation xml(@NonNull File xml) {
        xml_ = Objects.requireNonNull(xml, "The xml file must not be null");
        return this;
    }

    /**
     * Sets the location of the XML report.
     *
     * @param xml the report location
     * @return this operation instance
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JacocoReportOperation xml(@NonNull String xml) {
        ObjectTools.requireNotEmpty(xml, "The xml file must not be null or empty");
        return xml(new File(xml));
    }

    /**
     * Sets the location of the XML report.
     *
     * @param xml the report location
     * @return this operation instance
     */
    public JacocoReportOperation xml(@NonNull Path xml) {
        Objects.requireNonNull(xml, "The xml file must not be null");
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
        var matcher = PathMatcher.of(includes_, excludes_);

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

    // Retrieve the JaCoCo agent version.
    @SuppressFBWarnings("STT_STRING_PARSING_A_FIELD")
    private String getJacocoAgentVersion() throws ExitStatusException {
        var v = JaCoCo.VERSION; // "0.8.14" or "0.8.14.202510111229" or "0.8.15-SNAPSHOT"
        var parts = v.split("[.-]"); // split on. or -
        if (parts.length < 3) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.severe("Unexpected JaCoCo version format: " + v);
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
        return parts[0] + "." + parts[1] + "." + parts[2]; // "0.8.14"
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

    @SuppressWarnings({"PMD.CloseResource", "PMD.AvoidCatchingGenericException"})
    private IReportVisitor reportVisitor(@NonNull File xml, File csv, File htmlDirectory) throws IOException {
        OutputStream xmlStream = null;
        OutputStream csvStream = null;
        try {
            var visitors = new ArrayList<IReportVisitor>();

            xmlStream = Files.newOutputStream(xml.toPath()); // correct
            visitors.add(new XMLFormatter().createVisitor(xmlStream));

            csvStream = Files.newOutputStream(csv.toPath()); // correct
            visitors.add(new CSVFormatter().createVisitor(csvStream));

            visitors.add(new HTMLFormatter().createVisitor(new FileMultiReportOutput(htmlDirectory)));

            return new MultiReportVisitor(visitors);
        } catch (IOException | RuntimeException e) {
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
            throw e;
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

    @SuppressFBWarnings({"PATH_TRAVERSAL_IN"})
    private void writeReports(IBundleCoverage bundle,
                              ExecFileLoader loader,
                              File xml,
                              File csv,
                              File htmlDirectory,
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
            logger.log(Level.INFO, "XML Report: " + new File(xml.getAbsolutePath()).toURI());
            logger.log(Level.INFO, "CSV Report: " + new File(csv.getAbsolutePath()).toURI());
            logger.log(Level.INFO, "HTML Report: " + new File(htmlDirectory, "index.html").toURI());
        }
    }

    /**
     * Ant-style path matcher for include/exclude patterns.
     * <p>
     * Converts Ant patterns to regex: {@code **} -> {@code .*}, {@code *} -> {@code [^/]*}, {@code ?} -> {@code .}
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
                            sb.append(".*"); // ** -> any chars including /
                            i += 2; // consume both *
                        } else {
                            sb.append("[^/]*"); // * -> any chars except /
                            i++;
                        }
                    }
                    case '?' -> {
                        sb.append("[^/]"); // ? -> single char except /
                        i++;
                    }
                    case '.', '[', ']', '{', '}', '(', ')', '+', '^', '$', '|', '\\' -> {
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

            boolean included = includePatterns_.isEmpty() || includePatterns_.stream().anyMatch(p -> p.matcher(normalized).matches());

            return included && excludePatterns_.stream().noneMatch(p -> p.matcher(normalized).matches());
        }
    }
}