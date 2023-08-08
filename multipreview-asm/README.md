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

