# [bld](https://rife2.com/bld) Extension to Generate Code Coverage Reports with [JaCoCo](https://www.eclemma.org/jacoco/)


[![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/java-17%2B-blue)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![bld](https://img.shields.io/badge/1.9.1-FA9052?label=bld&labelColor=2392FF)](https://rife2.com/bld)
[![Release](https://flat.badgen.net/maven/v/metadata-url/repo.rife2.com/releases/com/uwyn/rife2/bld-jacoco-report/maven-metadata.xml?color=blue)](https://repo.rife2.com/#/releases/com/uwyn/rife2/bld-jacoco-report)
[![Snapshot](https://flat.badgen.net/maven/v/metadata-url/repo.rife2.com/snapshots/com/uwyn/rife2/bld-jacoco-report/maven-metadata.xml?label=snapshot)](https://repo.rife2.com/#/snapshots/com/uwyn/rife2/bld-jacoco-report)
[![GitHub CI](https://github.com/rife2/bld-jacoco-report/actions/workflows/bld.yml/badge.svg)](https://github.com/rife2/bld-jacoco-report/actions/workflows/bld.yml)

To install, please refer to the [extensions documentation](https://github.com/rife2/bld/wiki/Extensions).

To run the tests and generate the code coverage reports, add the floowing to your build file:

```java
@BuildCommand(summary = "Generates Jacoco Reports")
public void jacoco() throws Exception {
    new JacocoReportOperation()
            .fromProject(this)
            .execute();
}
```

```console
./bld compile jacoco
```

- [View Examples](https://github.com/rife2/bld-jacoco-report/tree/master/examples)

- The HTML, CSV and XML reports will be automatically created in the `build/reports/jacoco/test` directory.
- The execution coverage data will be automatically recorded in the `build/jacoco/jacoco.exec` file.

Please check the [JacocoReportOperation documentation](https://rife2.github.io/bld-jacoco-report/rife/bld/extension/JacocoReportOperation.html#method-summary) for all available configuration options.

### SonarQube/SonarCloud

To use a JaCoCo report with [sonar](https://www.sonarsource.com/), add something like the following to your `sonar-project.properties`:

```properties
sonar.organization=YOUR_ORGANIZATION
sonar.projectKey=YOUR_PROJECT_KEY
sonar.coverage.jacoco.xmlReportPaths=build/reports/jacoco/test/jacocoTestReport.xml
sonar.sources=src/main/java/
sonar.tests=src/test/java/
sonar.java.binaries=build/main,build/test
sonar.java.libraries=lib/compile/*.jar
```
