package com.example;

import rife.bld.BaseProject;
import rife.bld.BuildCommand;
import rife.bld.extension.JacocoReportOperation;

import java.util.List;

import static rife.bld.dependencies.Repository.MAVEN_CENTRAL;
import static rife.bld.dependencies.Repository.RIFE2_RELEASES;
import static rife.bld.dependencies.Scope.test;

public class ExamplesBuild extends BaseProject {
    public ExamplesBuild() {
        pkg = "com.example";
        name = "Examples";
        version = version(0, 1, 0);

        repositories = List.of(MAVEN_CENTRAL);

        scope(test)
                .include(dependency("org.junit.jupiter", "junit-jupiter", version(5, 9, 2)))
                .include(dependency("org.junit.platform", "junit-platform-console-standalone", version(1, 9, 2)));

        testOperation().mainClass("com.example.ExamplesTest");
    }

    public static void main(String[] args) {
        new ExamplesBuild().start(args);
    }

    @Override
    public void compile() throws Exception {
        super.compile();
        jacoco();
    }

    @BuildCommand(summary = "Generates Jacoco Reports")
    public void jacoco() throws Exception {
        new JacocoReportOperation()
                .fromProject(this)
                .execute();
    }
}