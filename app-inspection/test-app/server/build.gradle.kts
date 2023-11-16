plugins {
  id("java-library")
  alias(libs.plugins.kotlin)
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
  implementation(project(":shared"))
  implementation(libs.grpc.core)
  implementation(libs.grpc.protobuf.lite)
  implementation(libs.grpc.netty)
  implementation(libs.grpc.stub)
  implementation(libs.grpc.services)
  implementation(libs.kotlinx.cli)
}

val fatJar = task("fatJar", type = Jar::class) {
  archiveBaseName.set("server-app")
  configurations.runtimeClasspath.get().forEach {
    println(it)
  }
  manifest.attributes["Main-Class"] = "com.google.test.inspectors.grpc.GrpcServerKt"
  val dependencies = configurations
    .runtimeClasspath
    .get()
    .map(::zipTree) // OR .map { zipTree(it) }
  from(dependencies)
  with(tasks.jar.get() as CopySpec)
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
