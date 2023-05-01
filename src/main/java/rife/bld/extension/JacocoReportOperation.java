/*
 *  Copyright 2023 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package rife.bld.extension;

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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private final List<File> classFiles = new ArrayList<>();
    private final List<File> execFiles = new ArrayList<>();
    private final List<File> sourceFiles = new ArrayList<>();
    private File csv;
    private String encoding;
    private File html;
    private String name = "JaCoCo Coverage Report";
    private BaseProject project;
    private boolean quiet;
    private int tabWidth = 4;
    private File xml;

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
     * Set the locations of Java class files.
     **/
    public JacocoReportOperation classFiles(List<File> classFiles) {
        this.classFiles.addAll(classFiles);
        return this;
    }

    /**
     * Sets the location of the CSV report.
     */
    public JacocoReportOperation csv(File cvs) {
        this.csv = cvs;
        return this;
    }

    /**
     * Sets the source file encoding. The platform encoding is used by default.
     */
    public JacocoReportOperation encoding(String encoding) {
        this.encoding = encoding;
        return this;
    }

    /**
     * Sets a list of JaCoCo *.exec files to read.
     **/
    public JacocoReportOperation execFiles(List<File> execFiles) {
        this.execFiles.addAll(execFiles);
        return this;
    }

    /**
     * Performs the operation execution that can be wrapped by the {@code #executeOnce} call.
     *
     * @throws Exception when an exception occurs during the execution
     * @since 1.5.10
     */
    @Override

    public void execute() throws Exception {
        if (project == null && LOGGER.isLoggable(Level.SEVERE)) {
            LOGGER.severe("A project must be specified.");
        } else {

            var buildJacocoReportsDir = Path.of(project.buildDirectory().getPath(), "reports", "jacoco", "test").toFile();

            if (sourceFiles.isEmpty()) {
                sourceFiles.add(project.srcDirectory());
                //sourceFiles.add(project.srcTestDirectory());
            }

            if (classFiles.isEmpty()) {
                classFiles.add(project.buildMainDirectory());
                classFiles.add(project.buildTestDirectory());
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

            var loader = loadExecFiles();
            var bundle = analyze(loader.getExecutionDataStore());
            writeReports(bundle, loader);
        }
    }

    /**
     * Configure the operation from a {@link BaseProject}.
     */
    public JacocoReportOperation fromProject(BaseProject project) {
        this.project = project;
        return this;
    }

    /**
     * Sets the location of the HTML report.
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
                    LOGGER.log(Level.INFO, "Loading execution data file: {0}",
                            f.getAbsolutePath());
                }
                loader.load(f);
            }
        }
        return loader;
    }

    /**
     * Sets the name used for the report.
     */
    public JacocoReportOperation name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Suppresses all output.
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
     * Sets the locations of the source files.
     **/
    public JacocoReportOperation sourceFiles(List<File> sourceFiles) {
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
    }

    /**
     * Sets the location of the XML report.
     */
    public JacocoReportOperation xml(File xml) {
        this.xml = xml;
        return this;
    }

}