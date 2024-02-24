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

