buildscript {
  dependencies {
    classpath(libs.protobuf.gradle.plugin)
  }
}
@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
  id("java-library")
  alias(libs.plugins.protobuf)
  alias(libs.plugins.kotlin)
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

protobuf {
  protoc {
    artifact = libs.protoc.get().toString()
  }
  plugins {
    create("grpc") {
      artifact = libs.grpc.protoc.gen.java.get().toString()
    }
    create("grpckt") {
      artifact = "${libs.grpc.protoc.gen.kotlin.get()}:jdk8@jar"
    }
  }
  generateProtoTasks {
    all().forEach {
      it.builtins {
        named("java") {
          option("lite")
        }
        create("kotlin") {
          option("lite")
        }
      }
      it.plugins {
        create("grpc") {
          option("lite")
        }
        create("grpckt") {
          option("lite")
        }
      }
    }
  }
}

dependencies {
  implementation(libs.checker.qual)
  implementation(libs.grpc.kotlin.stub)
  implementation(libs.grpc.protobuf.lite)
  implementation(libs.grpc.stub)
  implementation(libs.gson)
  implementation(libs.guava)
  implementation(libs.javax.annotation.api)
  implementation(libs.kotlinx.coroutines.core)

  compileOnly(libs.protobuf.kotlin)
}