# Multipreview ASM

This module contains functionality that can build so-called
"[multipreview](https://go/compose-multipreview)" annotation structure
from the bytecode. We also have similar functionality in Android Studio,
however, that one extracts multipreview structure using Psi framework
of Intellij IDEA.

It might be problematic (but still possible, see `lint-psi` for more
information) to use Psi framework outside of Android Studio. Some of the
reasons are:
 * Psi file structure needs to be explicitly built outside of Studio (in
Studio it is "given")
 * Psi carries additional dependencies on Intellij IDEA framework
 * Current out of studio Psi solution is dedicated to lint use case, and
it is not recommended to use it [b/293505436](https://b/293505436)

Therefore, an alternative approach that does not use Psi has been
developed.

## Terminology

Originally, multipreview concept was developed for the case of Compose.
However, as we started to have more annotations indicating preview the
same approach became possible for other annotations as well. To take
this into account, the `@Preview` (Compose) annotation and its analogs
for other previews is called `Base` annotation, while the multipreview
annotations (the annotations annotated by the `Base` annotation and
other multipreview annotations) are called `Derived` annotations.

## API

The library provides an API to extract multipreview information from the
provided bytecode. One needs to specify a number of settings parameters.
The mandatory one is `MultipreviewSettings`:

```kotlin
data class MultipreviewSettings(
    val baseAnnotation: String,
    val parameterAnnotation: String
)
```

`baseAnnotation` should specify the fully qualified class name of the
base annotation for multipreview, e.g. for compose view previews that
would be `androidx.compose.ui.tooling.preview.Preview`

`parameterAnnotation` should specify the fully qualified class name of
the annotation that is used to inject parameters to the previewable
function. Again, in case of compose view that would be
`androidx.compose.ui.tooling.preview.PreviewParameter`

Another optional is `MethodsFilter`:

```kotlin
fun interface MethodsFilter {
    fun allowMethod(methodFqn: String): Boolean
}
```

It allows to skip some methods (functions) to speed up the processing.
By default, all methods are inspected.

Having those parameters and classes to process one can call one of the
following `buildMultipreview` functions:

```kotlin
fun buildMultipreview(
  settings: MultipreviewSettings,
  methodsFilter: MethodsFilter = MethodsFilter { true },
  provider: ClassBytecodeProvider,
): Multipreview
```

or

```kotlin
fun buildMultipreview(
  settings: MultipreviewSettings,
  paths: Collection<String>,
  methodsFilter: MethodsFilter = MethodsFilter { true }
): Multipreview
```

`buildMultipreview` returns an instance of `Multipreview` class. It
provides a set of found previewable methods and allows to request a set
of found (parameterized) annotations for each method:

```kotlin
interface Multipreview {
  val methods: Set<MethodRepresentation>
  fun getAnnotations(method: MethodRepresentation): Set<BaseAnnotationRepresentation>
}
```
