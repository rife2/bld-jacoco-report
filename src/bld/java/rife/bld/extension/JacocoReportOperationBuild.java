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

import rife.bld.BuildCommand;
import rife.bld.Project;
import rife.bld.dependencies.VersionNumber;
import rife.bld.publish.PublishDeveloper;
import rife.bld.publish.PublishLicense;
import rife.bld.publish.PublishScm;

import java.util.List;

import static rife.bld.dependencies.Repository.*;
import static rife.bld.dependencies.Scope.*;
import static rife.bld.operations.JavadocOptions.DocLinkOption.NO_MISSING;

public class JacocoReportOperationBuild extends Project {
    public JacocoReportOperationBuild() {
        pkg = "rife.bld.extension";
        name = "bld-jacoco-report";
        version = version(0, 9, 0, "SNAPSHOT");

        javaRelease = 17;
        downloadSources = true;
        autoDownloadPurge = true;
        repositories = List.of(MAVEN_CENTRAL, RIFE2_RELEASES);

        var jacocoVersion = new VersionNumber(0, 8, 10);
        var rife2 = version(1, 7, 0);
        scope(compile)
                .include(dependency("org.jacoco", "jacoco", jacocoVersion).exclude("*", "org.jacoco.doc"))
                .include(dependency("com.uwyn.rife2", "rife2", rife2))
                .include(dependency("com.uwyn.rife2", "bld", rife2));
        scope(runtime)
                .include(dependency("org.jacoco", "jacoco", jacocoVersion).exclude("*", "org.jacoco.doc"));
        scope(test)
                .include(dependency("org.junit.jupiter", "junit-jupiter", version(5, 10, 0)))
                .include(dependency("org.junit.platform", "junit-platform-console-standalone", version(1, 10, 0)))
                .include(dependency("org.assertj:assertj-joda-time:2.2.0"));

        testOperation().mainClass("rife.bld.extension.JacocoReportOperationTest");

        javadocOperation()
                .javadocOptions()
                .docLint(NO_MISSING)
                .link("https://rife2.github.io/rife2/");

        publishOperation()
                .repository(version.isSnapshot() ? repository("rife2-snapshot") : repository("rife2"))
                // .repository(MAVEN_LOCAL)
                .info()
                .groupId("com.uwyn.rife2")
                .artifactId("bld-jacoco-report")
                .description("bld Extension to Generate JaCoCo Reports")
                .url("https://github.com/rife2/bld-pmd")
                .developer(new PublishDeveloper().id("ethauvin").name("Erik C. Thauvin").email("erik@thauvin.net")
                        .url("https://erik.thauvin.net/"))
                .license(new PublishLicense().name("The Apache License, Version 2.0")
                        .url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
                .scm(new PublishScm().connection("scm:git:https://github.com/rife2/bld-pmd.git")
                        .developerConnection("scm:git:git@github.com:rife2/bld-pmd.git")
                        .url("https://github.com/rife2/bld-pmd"))
                .signKey(property("sign.key"))
                .signPassphrase(property("sign.passphrase"));
    }

    public static void main(String[] args) {
        new JacocoReportOperationBuild().start(args);
    }

    @BuildCommand(summary = "Runs PMD analysis")
    public void pmd() throws Exception {
        new PmdOperation()
                .fromProject(this)
                .failOnViolation(true)
                .ruleSets("config/pmd.xml")
                .execute();
    }
}