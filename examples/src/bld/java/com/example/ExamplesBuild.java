package com.example;

import rife.bld.BuildCommand;
import rife.bld.Project;
import rife.bld.extension.JacocoReportOperation;

import java.io.IOException;
import java.util.List;

import static rife.bld.dependencies.Repository.MAVEN_CENTRAL;
import static rife.bld.dependencies.Repository.MAVEN_LOCAL;
import static rife.bld.dependencies.Scope.test;

public class ExamplesBuild extends Project {
    public ExamplesBuild() {
        pkg = "com.example";
        name = "Examples";
        version = version(0, 1, 0);

        javaRelease = 17;

        downloadSources = true;
        autoDownloadPurge = true;

        repositories = List.of(MAVEN_LOCAL, MAVEN_CENTRAL);

        scope(test)
                .include(dependency("org.junit.jupiter", "junit-jupiter", version(5, 11, 4)))
                .include(dependency("org.junit.platform", "junit-platform-console-standalone", version(1, 11, 4)));
    }

    public static void main(String[] args) {
        new ExamplesBuild().start(args);
    }

    @BuildCommand(summary = "Generates Jacoco Reports")
    public void jacoco() throws Exception {
        new JacocoReportOperation()
                .fromProject(this)
                .execute();
    }
}
