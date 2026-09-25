// A sample BlockDesigner plugin. Build it with:  ./gradlew :examples:hello-plugin:jar
// then copy build/libs/hello-plugin-*.jar into BlockDesigner's plugins folder (Plugins > Manage plugins > Open folder).
//
// Outside this repository, depend on the API jar instead:  compileOnly(files("libs/blockdesigner-plugin-api.jar"))
// plus the core jar it exposes. The app provides both at runtime, so never bundle them into your plugin.
dependencies {
    compileOnly(project(":plugin-api"))
}
