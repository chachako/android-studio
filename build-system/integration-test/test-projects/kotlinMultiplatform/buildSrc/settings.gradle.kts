apply(from="../../commonLocalRepo.gradle", to=pluginManagement)
apply(from="../../versionCatalog.gradle")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    apply(from="../../commonLocalRepo.gradle", to=dependencyResolutionManagement)
}
