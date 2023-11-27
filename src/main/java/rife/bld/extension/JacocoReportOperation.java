/*
 * Copyright 2023 the original author or authors.
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
    final List<File> classFiles = new ArrayList<>();
    /**
     * The location of the exec files.
     */
    final List<File> execFiles = new ArrayList<>();
    /**
     * The location of the source files.
     */
    final List<File> sourceFiles = new ArrayList<>();
    /**
     * The location of the CSV report.
     */
    File csv;
    /**
     * The file to write execution data to.
     */
    File destFile;
    /**
     * The source file encoding.
     */
    String encoding;
    /**
     * The location of the HTML report.
     */
    File html;
    /**
     * The report name.
     */
    String name = "JaCoCo Coverage Report";
    /**
     * The project reference.
     */
    BaseProject project;
    /**
     * The quiet flag.
     */
    boolean quiet;
    /**
     * THe tab width.
     */
    int tabWidth = 4;
    /**
     * THe location of the XML report
     */
    File xml;


    private IBundleCoverage analyze(ExecutionDataStore data) throws IOException {
        var builder = new CoverageBuilder();
        var analyzer = new Analyzer(data, builder);
        for (var f : classFiles) {
            LOGGER.info(f.getAbsolutePath());
            analyzer.analyzeAll(f);
        }
        return builder.getBundle(name);
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     */
    public JacocoReportOperation classFiles(File... classFiles) {
        this.classFiles.addAll(List.of(classFiles));
        return this;
    }

    /**
     * Sets the locations of Java class files.
     *
     * @param classFiles the class files
     * @return this operation instance
     */
    public JacocoReportOperation classFiles(Collection<File> classFiles) {
        this.classFiles.addAll(classFiles);
        return this;
    }

    /**
     * Sets the location of the CSV report.
     *
     * @param cvs the report location
     * @return this operation instance
     */
    public JacocoReportOperation csv(File cvs) {
        this.csv = cvs;
        return this;
    }

    /**
     * Sets the file to write execution data to.
     *
     * @param destFile the file
     * @return this operation instance
     */
    public JacocoReportOperation destFile(File destFile) {
        this.destFile = destFile;
        return this;
    }

    /**
     * Sets the source file encoding. The platform encoding is used by default.
     *
     * @param encoding the encoding
     * @return this operation instance
     */
    public JacocoReportOperation encoding(String encoding) {
        this.encoding = encoding;
        return this;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     *
     * @param execFiles the exec files
     * @return this operation instance
     */
    public JacocoReportOperation execFiles(File... execFiles) {
        this.execFiles.addAll(List.of(execFiles));
        return this;
    }

    /**
     * Sets the locations of the JaCoCo *.exec files to read.
     *
     * @param execFiles the exec files
     * @return this operation instance
     */
    public JacocoReportOperation execFiles(Collection<File> execFiles) {
        this.execFiles.addAll(execFiles);
        return this;
    }

    /**
     * Performs the operation execution that can be wrapped by the {@code #executeOnce} call.
     */
    @Override
    public void execute() throws IOException {
        if ((project == null) && LOGGER.isLoggable(Level.SEVERE)) {
            LOGGER.severe("A project must be specified.");
        } else {
            var buildJacocoReportsDir = Path.of(project.buildDirectory().getPath(), "reports", "jacoco", "test").toFile();
            var buildJacocoExecDir = Path.of(project.buildDirectory().getPath(), "jacoco").toFile();
            var buildJacocoExec = Path.of(buildJacocoExecDir.getPath(), "jacoco.exec").toFile();

            if (destFile == null) {
                destFile = Path.of(buildJacocoExecDir.getPath(), "jacoco.exec").toFile();
            }

            if (execFiles.isEmpty()) {
//            project.testOperation().fromProject(project).javaOptions().javaAgent(
//                    Path.of(project.libBldDirectory().getPath(), "org.jacoco.agent-"
//                            + JaCoCo.VERSION.substring(0, JaCoCo.VERSION.lastIndexOf('.')) + "-runtime.jar").toFile(),
//                    "destfile=" + destFile.getPath());
                project.testOperation().fromProject(project).javaOptions().add("-javaagent:" +
                        Path.of(project.libBldDirectory().getPath(), "org.jacoco.agent-"
                                + JaCoCo.VERSION.substring(0, JaCoCo.VERSION.lastIndexOf('.')) + "-runtime.jar")
                        + "=destfile=" + destFile.getPath());
                try {
                    project.testOperation().execute();
                } catch (InterruptedException | ExitStatusException e) {
                    throw new IOException(e);
                }

                if (LOGGER.isLoggable(Level.INFO) && !quiet) {
                    LOGGER.log(Level.INFO, "Execution Data: {0}", destFile);
                }

                if (buildJacocoExec.exists()) {
                    execFiles.add(buildJacocoExec);
                }
            }

            if (sourceFiles.isEmpty()) {
                sourceFiles.add(project.srcMainJavaDirectory());
            }

            if (classFiles.isEmpty()) {
                classFiles.add(project.buildMainDirectory());
            }

            if (html == null) {
                html = new File(buildJacocoReportsDir, "html");
            }

            if (xml == null) {
                xml = new File(buildJacocoReportsDir, "jacocoTestReport.xml");
            }

            if (csv == null) {
                csv = new File(buildJacocoReportsDir, "jacocoTestReport.csv");
            }

            //noinspection ResultOfMethodCallIgnored
            buildJacocoReportsDir.mkdirs();
            //noinspection ResultOfMethodCallIgnored
            buildJacocoExecDir.mkdirs();

            var loader = loadExecFiles();
            var bundle = analyze(loader.getExecutionDataStore());
            writeReports(bundle, loader);
        }
    }

    /**
     * Configure the operation from a {@link BaseProject}.
     *
     * @param project the project
     * @return this operation instance
     */
    public JacocoReportOperation fromProject(BaseProject project) {
        this.project = project;
        return this;
    }

    /**
     * Sets the location of the HTML report.
     *
     * @param html the html
     * @return this operation instance
     */
    public JacocoReportOperation html(File html) {
        this.html = html;
        return this;
    }

    private ExecFileLoader loadExecFiles() throws IOException {
        var loader = new ExecFileLoader();
        if (execFiles.isEmpty() && LOGGER.isLoggable(Level.WARNING) && !quiet) {
            LOGGER.warning("No execution data files provided.");
        } else {
            for (var f : execFiles) {
                if (LOGGER.isLoggable(Level.INFO) && !quiet) {
                    LOGGER.log(Level.INFO, "Loading execution data: {0}",
                            f.getAbsolutePath());
                }
                loader.load(f);
            }
        }
        return loader;
    }

    /**
     * Sets the name used for the report.
     *
     * @param name the name
     * @return this operation instance
     */
    public JacocoReportOperation name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Suppresses all output.
     *
     * @param quiet {@code true} or {@code false}
     * @return this operation instance
     */
    public JacocoReportOperation quiet(boolean quiet) {
        this.quiet = quiet;
        return this;
    }

    private IReportVisitor reportVisitor() throws IOException {
        List<IReportVisitor> visitors = new ArrayList<>();

        if (xml != null) {
            var formatter = new XMLFormatter();
            visitors.add(formatter.createVisitor(Files.newOutputStream(xml.toPath())));
        }

        if (csv != null) {
            var formatter = new CSVFormatter();
            visitors.add(formatter.createVisitor(Files.newOutputStream(csv.toPath())));
        }

        if (html != null) {
            var formatter = new HTMLFormatter();
            visitors.add(formatter.createVisitor(new FileMultiReportOutput(html)));
        }

        return new MultiReportVisitor(visitors);
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     */
    public JacocoReportOperation sourceFiles(File... sourceFiles) {
        this.sourceFiles.addAll(List.of(sourceFiles));
        return this;
    }

    /**
     * Sets the locations of the source files. (e.g., {@code src/main/java})
     *
     * @param sourceFiles the source files
     * @return this operation instance
     */
    public JacocoReportOperation sourceFiles(Collection<File> sourceFiles) {
        this.sourceFiles.addAll(sourceFiles);
        return this;
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private ISourceFileLocator sourceLocator() {
        var multi = new MultiSourceFileLocator(tabWidth);
        for (var f : sourceFiles) {
            multi.add(new DirectorySourceFileLocator(f, encoding, tabWidth));
        }
        return multi;
    }

    /**
     * Sets the tab stop width for the source pages. Default is {@code 4}.
     *
     * @param tabWidth the tab width
     * @return this operation instance
     */
    public JacocoReportOperation tabWidth(int tabWidth) {
        this.tabWidth = tabWidth;
        return this;
    }

    private void writeReports(IBundleCoverage bundle, ExecFileLoader loader)
            throws IOException {
        if (LOGGER.isLoggable(Level.INFO) && !quiet) {
            LOGGER.log(Level.INFO, "Analyzing {0} classes.",
                    bundle.getClassCounter().getTotalCount());
        }
        IReportVisitor visitor = reportVisitor();
        visitor.visitInfo(loader.getSessionInfoStore().getInfos(),
                loader.getExecutionDataStore().getContents());
        visitor.visitBundle(bundle, sourceLocator());
        visitor.visitEnd();
        if (LOGGER.isLoggable(Level.INFO) && !quiet) {
            LOGGER.log(Level.INFO, "XML Report: file://{0}", xml);
            LOGGER.log(Level.INFO, "CSV Report: file://{0}", csv);
            LOGGER.log(Level.INFO, "HTML Report: file://{0}/index.html", html);
        }
    }

    /**
     * Sets the location of the XML report.
     *
     * @param xml the report location
     * @return this operation instance
     */
    public JacocoReportOperation xml(File xml) {
        this.xml = xml;
        return this;
    }
}