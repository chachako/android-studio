### How to update API-related generated files

current.txt and apiLevels.json are auto-generated files produced by the metalava plugin.
To update those files you can run:

```
$ ./gradlew :base:build-system:gradle-settings-api:updateMetalavaApi
```
