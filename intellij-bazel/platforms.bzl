load("//tools/base/intellij-bazel:intellij.bzl", "local_platform", "remote_platform", "setup_platforms")

def setup_intellij_platforms():
    setup_platforms([
        local_platform(
            name = "studio-sdk",
            target = "@//prebuilts/studio/intellij-sdk:studio-sdk",
            spec = "@//prebuilts/studio/intellij-sdk:AI/spec.bzl",
        ),
        remote_platform(
            name = "intellij_ce_2023_3",
            url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/233.11799.241/ideaIC-233.11799.241.zip",
            sha256 = "eb9993202e137a3cbea81fcd42235ed5519055ab1aa58c3b0b724fb68c22f6d3",
        ),
        remote_platform(
            name = "intellij_ce_2024_1",
            url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/241.18034.62/ideaIC-241.18034.62.zip",
            sha256 = "47c62827d54d60e012bd28e8295a645ba3cae86f3652a8fa4ff0206d8e090943",
        ),
        remote_platform(
            name = "intellij_ce_2024_2",
            url = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/ideaIC/242.20224.91-EAP-SNAPSHOT/ideaIC-242.20224.91-EAP-SNAPSHOT.zip",
            sha256 = "c90e24c1229a03770957d7a416023bf240e66fcad0213de6115bc7725f4eba39",
        ),
    ])
