## Inspection Testing Dependency

This dependency is owned by the `androidx-main` repository.

See go/androidx-dev for startup instructions.

Once repo is installed:

```shell
cd frameworks/support
./gradlew inspection:inspection-testing:createFullJarDebug
./gradlew inspection:inspection-testing:debugSourcesJar

cp ../../out/androidx/inspection/inspection-testing/build/intermediates/full_jar/debug/createFullJarDebug/full.jar <lib-dir>/inspection-testing-1.0.0.jar
cp ../../out/androidx/inspection/inspection-testing/build/libs/inspection-testing-debug-1.0.0-sources.jar <lib-dir>
```

## AndroidX Test Monitor Dependency

This dependency is the `classes.jar` from inside `@maven://androidx.test.monitor` AAR file.

It was created here with:

```
jar xvf prebuilts/tools/common/m2/repository/androidx/test/monitor/1.6.1/monitor-1.6.1.aar \
        tools/base/app-inspection/inspectors/lib/androidx-test-monitor-1.6.1.jar
```
