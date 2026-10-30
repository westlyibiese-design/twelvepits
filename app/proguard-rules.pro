# Keep rules for the release build (R8). The libraries (Compose, Navigation, Lifecycle, DataStore, Media3)
# bring their own rules, so only what is special to this game is listed here.

# 1. Crash reports. Keep the file names and line numbers so a stack trace can be read with the mapping file
#    that is built with every release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 2. Enums. The saved game, the scores, the settings and the navigation routes store enum values by their
#    name (for example "NORMAL" or "KING"). Keeping the enums as they are means a shrunk build can never
#    change, merge or turn them into numbers, so a game saved by one version is read by the next one.
-keep enum com.westly.twelvepits.** { *; }
