plugins {
    java
}

applyPaperweightAdapterConfiguration(
        "1.17.1-R0.1-20211001.111503-102"
)

repositories {
    maven {
        name = "PaperMC"
        url = uri("https://papermc.io/repo/repository/maven-public/")
        content {
            includeModule("io.papermc", "paperlib")
        }
    }
}

dependencies {
    compileOnly(libs.paperlib)
}
