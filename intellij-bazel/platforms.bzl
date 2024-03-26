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
            url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2023.3.5/ideaIC-2023.3.5.zip",
            sha256 = "ffa6c0f98bbf67286eefb8bfad0026029edcd334463fffc17f369d9a90156992",
        ),
        remote_platform(
            name = "intellij_ce_2024_1",
            url = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/ideaIC/241.14494.17-EAP-SNAPSHOT/ideaIC-241.14494.17-EAP-SNAPSHOT.zip",
            sha256 = "0a18e02b611562b4e81f2f5af570975c153874154ad17fdb026eae3b6818e422",
        ),
    ])
