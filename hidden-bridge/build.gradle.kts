plugins {
    `java-library`
}

// Subclasses of hidden framework classes. The stubs source set only satisfies javac;
// it is never packaged, so the device's boot class path supplies the real classes.
val stubs: SourceSet = sourceSets.create("stubs")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(stubs.output)
}
