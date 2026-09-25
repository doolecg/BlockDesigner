// The API third-party plugins compile against. Keep it small and stable: bump PluginApi.VERSION on breaking changes.
dependencies {
    api(project(":core"))
}

tasks.named<Jar>("jar") {
    archiveBaseName = "blockdesigner-plugin-api"
}
