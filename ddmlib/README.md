# DDMLib

For developers, read [architecture.md](architecture.md).

# Logging

ddmlib defines its own custom `AdbLogOutput`. By default Android Studio logs
everything under info/warn/error. To also include "debug" level, open the
"Debug Log Settings" and add the following line.

```
#com.android.ddmlib:all
```
