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

import rife.bld.BaseProject;
import rife.bld.operations.AbstractOperation;
import rife.tools.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

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
    private String name;
    private BaseProject project;
    private boolean quiet = false;
    private int tabWidth = 4;
    private File xml;

    /**
     * Set the locations of Java class files.
     **/
    public JacocoReportOperation classFiles(ArrayList<File> classFiles) {
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
    public JacocoReportOperation execFiles(ArrayList<File> execFiles) {
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

            if (execFiles.isEmpty()) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.warning("No execution data files provided.");
                }
            }

            if (sourceFiles.isEmpty()) {
                sourceFiles.addAll(project.mainSourceFiles());
                sourceFiles.addAll(project.testSourceFiles());
            }

            if (classFiles.isEmpty()) {
                classFiles.addAll(getClassFiles(project.buildMainDirectory()));
                classFiles.addAll(getClassFiles(project.buildTestDirectory()));
            }
        }
    }

    private List<File> getClassFiles(File directory) {
        return FileUtils.getFileList(directory, Pattern.compile("^.*\\.class$"), null)
                .stream().map(f -> new File(directory, f)).toList();
    }

    /**
     * Configure the operation from a {@link BaseProject}.
     */
    public JacocoReportOperation fromProject(BaseProject project) {
        this.project = project;
        return this;
    }

    public String getMessage() {
        return "Hello World!";
    }

    /**
     * Sets the location of the HTML report.
     */
    public JacocoReportOperation html(File html) {
        this.html = html;
        return this;
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

    /**
     * Sets the locations of the source files.
     **/
    public JacocoReportOperation sourceFiles(ArrayList<File> sourceFiles) {
        this.sourceFiles.addAll(sourceFiles);
        return this;
    }

    /**
     * Sets the tab stop width for the source pages. Default is {@code 4}.
     */
    public JacocoReportOperation tabWidth(int tabWitdh) {
        this.tabWidth = tabWitdh;
        return this;
    }

    /**
     * Sets the location of the XML report.
     */
    public JacocoReportOperation xml(File xml) {
        this.xml = xml;
        return this;
    }
}