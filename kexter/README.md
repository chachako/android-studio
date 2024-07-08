# Kexter Library

## Usage
Kexter is the library powering Android Studio Java/Kotlin debugger.
It allows inspection of both dex files and ART bytecode. See documentation for
[dex format](https://source.android.com/docs/core/runtime/dex-format) and
[dalvik-bytecode](https://source.android.com/docs/core/runtime/dalvik-bytecode).


## Goal
Kexter is intended for runtime usage. It is build for speed and low
memory consumption. It avoids doing anything until the data is requested.
The library extensively uses lazy construct and cache the results.

## Architecture
The internal part of kexter is called "core", all classes there
are internal visibility.

## Tools
To test kexter beyond unit tests, you can run `apkdumper` on any apk file.

```
tools/base/bazel/bazel run //tools/base/kexter:apkdumper <APK_PATH>
```


## Usage Example

### Dex inspection

```
val dex = Dex.fromBytes(bytes)
dex.classes.values.forEach { clazz ->
    println("${clazz.name}")
    clazz.methods.values.forEach { m ->
         println("    ${m.name}")
         m.byteCode.instructions.forEach { i->
             println("        ${i.opcode.name}")
         }
    }
}
```

### Direct Bytecode inspection
```
val instructions = DexBytecode.fromBytes(bytes)
instructions.forEach { instruction->
   println("    ${instruction.opcode.name}")
}
``
