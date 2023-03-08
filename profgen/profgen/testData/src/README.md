sources for testdata to get hello.{jar,apk}, compile with javac, then run d8.
For example:
```
javac Hello.java
zip hello.jar Hello.class
d8 Hello.class --output hello.apk
```
