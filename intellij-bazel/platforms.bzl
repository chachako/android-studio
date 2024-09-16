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
            url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/242.21829.142/ideaIC-242.21829.142.zip",
            sha256 = "e9ad86b7bbbfac801a863fa914714549ea5010968b27846de481621afbde9f1e",
        ),
        remote_platform(
            name = "intellij_ce_2024_3",
            url = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/ideaIC/243.12818.47-EAP-SNAPSHOT/ideaIC-243.12818.47-EAP-SNAPSHOT.zip",
            sha256 = "0efaffacac39b30718f5125a9cf3a95c4cb253822c264f3bc34e2784924925b0",
        ),
    ])
