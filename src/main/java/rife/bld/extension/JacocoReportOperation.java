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
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.OfflineInstrumentationAccessGenerator;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.*;
import org.jacoco.report.csv.CSVFormatter;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;
import rife.bld.BaseProject;
import rife.bld.operations.JUnitOperation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Generates <a href="https://www.jacoco.org/jacoco">JaCoCo</a> reports.
 *
 * @author <a href="https://erik.thauvin.net/">Erik C. Thauvin</a>
 * @since 1.0
 */
public class JacocoReportOperation extends JUnitOperation {
    private static final Logger LOGGER = Logger.getLogger(JacocoReportOperation.class.getName());
    private final List<File> classFiles = new ArrayList<>();
    private final List<File> execFiles = new ArrayList<>();
    private final List<File> instrumentedFiles = new ArrayList<>();
    private final List<File> sourceFiles = new ArrayList<>();
    private File csv;
    private String encoding;
    private File html;
    private Instrumenter instrumenter;
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

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private void buildInstrumentedFiles(File dest) throws IOException {
        var total = 0;
        for (var f : classFiles) {
            if (f.isFile()) {
                total += instrument(f, new File(dest, f.getName()));
            } else {
                total += instrumentRecursive(f, dest);
            }
        }
        if (LOGGER.isLoggable(Level.INFO) && !quiet) {
            LOGGER.log(Level.INFO, "{0} classes instrumented to {1}.",
                    new Object[]{total, dest.getAbsolutePath()});
        }
    }

    /**
     * Sets the locations of Java class files.
     **/
    public JacocoReportOperation classFiles(File... classFiles) {
        this.classFiles.addAll(Arrays.stream(classFiles).toList());
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
     * Sets the locations of the JaCoCo *.exec files to read.
     **/
    public JacocoReportOperation execFiles(File... execFiles) {
        this.execFiles.addAll(Arrays.stream(execFiles).toList());
        return this;
    }

    /**
     * Performs the operation execution that can be wrapped by the {@code #executeOnce} call.
     *
     * @since 1.5.10
     */
    @Override

    public void execute() throws IOException {
        if (project == null && LOGGER.isLoggable(Level.SEVERE)) {
            LOGGER.severe("A project must be specified.");
        } else {
            var buildJacocoReportsDir = Path.of(project.buildDirectory().getPath(), "reports", "jacoco", "test").toFile();
            var buildJacocoExecDir = Path.of(project.buildDirectory().getPath(), "jacoco").toFile();
            var buildJacocoClassesDir = Path.of(buildJacocoExecDir.getPath(), "classes").toFile();

            instrumenter = new Instrumenter(new OfflineInstrumentationAccessGenerator());
            instrumenter.setRemoveSignatures(true);

            if (sourceFiles.isEmpty()) {
                sourceFiles.add(project.srcDirectory());
                sourceFiles.add(project.srcTestDirectory());
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

            if (execFiles.isEmpty()) {
                //noinspection ResultOfMethodCallIgnored
                buildJacocoClassesDir.mkdirs();

                buildInstrumentedFiles(buildJacocoClassesDir);
                var files = buildJacocoClassesDir.listFiles();
                if (files != null) {
                    instrumentedFiles.addAll(Arrays.asList(files));
                }
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
    @Override
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

    private int instrument(final File src, final File dest) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        dest.getParentFile().mkdirs();
        try (InputStream input = Files.newInputStream(src.toPath())) {
            try (OutputStream output = Files.newOutputStream(dest.toPath())) {
                return instrumenter.instrumentAll(input, output,
                        src.getAbsolutePath());
            }
        } catch (final IOException e) {
            //noinspection ResultOfMethodCallIgnored
            dest.delete();
            throw e;
        }
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private int instrumentRecursive(final File src, final File dest)
            throws IOException {
        var total = 0;
        if (src.isDirectory()) {
            var listFiles = src.listFiles();
            if (listFiles != null) {
                for (var child : listFiles) {
                    total += instrumentRecursive(child,
                            new File(dest, child.getName()));
                }
            }
        } else {
            total += instrument(src, dest);
        }
        return total;
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
    public JacocoReportOperation sourceFiles(File... sourceFiles) {
        this.sourceFiles.addAll(Arrays.stream(sourceFiles).toList());
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