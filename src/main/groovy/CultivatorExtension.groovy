import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.tasks.Jar

import javax.inject.Inject

abstract class CultivatorExtension {

    private Project project

    @Inject
    CultivatorExtension(Project project) {
        this.project = project
    }

    void config(Action<CultivatorConfig> configAction) {
        Logger logger = project.logger
        // Apply config
        CultivatorConfig config = new CultivatorConfig()
        configAction.execute(config)
        // VALIDATION
        if (!config.minecraftVersion) {
            logger.info("VERSION: %s".formatted(config.minecraftVersion))
            throw new Exception("No `minecraftVersion` specified!")
        }
        // [1] Look at Version Manifest for version information
        logger.info("Looking for minecraft '%s' jar...".formatted(config.minecraftVersion))
        var manifestJson = UrlUtils.jsonFromUrl(CultivatorBuildConstants.VERSION_MANIFEST_V2_URL)
        var versions = manifestJson.get("versions").getAsJsonArray()
        String versionJsonUrl = null
        // Check if the requested version exists and grab the Version json
        for (var version in versions) {
            var versionObject = version.getAsJsonObject()
            String id = versionObject.get("id").getAsString()
            if (id == config.minecraftVersion) {
                versionJsonUrl = versionObject.get("url").getAsString()
                break
            }
        }
        // Make sure we actually found a url
        if (versionJsonUrl == null) {
            throw new Exception("ERROR - '" + config.minecraftVersion + "' is not a supported Minecraft version!")
        }

        // [2] Get URL for client jar and coordinates of libraries
        var versionJson = UrlUtils.jsonFromUrl(versionJsonUrl)
        var downloads = versionJson.get("downloads").getAsJsonObject()
        var libraries = versionJson.get("libraries").getAsJsonArray()

        // [2.1] Register task for client download
        var clientJarUrl = downloads.get("client").getAsJsonObject().get("url").getAsString()
        CultivatorConfiguredPaths.versionedCacheDir = new File(config.cacheDir + "/versions/" + config.minecraftVersion)
        CultivatorConfiguredPaths.clientJarFile = new File(CultivatorConfiguredPaths.versionedCacheDir.toString() + "/" + config.minecraftVersion + ".jar")
        CultivatorTasks.downloadJarTask = project.tasks.register("downloadJar", DownloadClientJarTask) {
            group=CultivatorBuildConstants.CULTIVATOR_TASK_GROUP
            downloadUrl.set(clientJarUrl)
            outputFile.set(CultivatorConfiguredPaths.clientJarFile)
        }
        project.dependencies.add("implementation", project.files(CultivatorConfiguredPaths.clientJarFile))
        logger.info("Found minecraft jar!")

        // [2.2] Now get the library jars
        logger.info("Locating library dependencies")
        project.configurations.register(CultivatorBuildConstants.MINECRAFT_DEPENDENCIES_CONFIGURATION_NAME) {

        }
        String currentOs = OperatingSystem.current().toString().toLowerCase()
        for (var library in libraries) {
            boolean shouldInclude = false
            var libraryObject = library.getAsJsonObject()
            if (libraryObject.has("rules")) {
                var rules = libraryObject.get("rules").getAsJsonArray()
                for (var ruleEntry in rules) {
                    var rule = ruleEntry.getAsJsonObject()
                    if(rule.get("action").getAsString() == "allow") {
                        String os = rule.get("os").getAsJsonObject().get("name").getAsString().toLowerCase()
                        if(currentOs.contains(os)) {
                            shouldInclude = true
                            break
                        }
                    }
                }
            }
            else {
                shouldInclude = true
            }
            if (shouldInclude) {
                var libraryCoordinate = libraryObject.get("name").getAsString()
                logger.info("Adding dependency '%s'".formatted(libraryCoordinate))
                project.dependencies.add("implementation", libraryCoordinate)
                project.dependencies.add(CultivatorBuildConstants.MINECRAFT_DEPENDENCIES_CONFIGURATION_NAME, libraryCoordinate)
            }
        }

        // [2.1.1] Register decompilation tasks
        CultivatorConfiguredPaths.devDirectory = new File(config.devDir)
        CultivatorConfiguredPaths.decompilerOutput = new File(CultivatorConfiguredPaths.versionedCacheDir.toString() + "/decompiled")
        CultivatorTasks.decompileJarTask = project.tasks.register("decompileJar", DecompileMinecraftTask) {
            group=CultivatorBuildConstants.CULTIVATOR_TASK_GROUP
            minecraftJar.set(CultivatorTasks.downloadJarTask.get().getOutputFile())
            decompilerOutputPath.set(CultivatorConfiguredPaths.decompilerOutput)
            minecraftLibraries.set(project.files(project.configurations.named(CultivatorBuildConstants.MINECRAFT_DEPENDENCIES_CONFIGURATION_NAME).get()))
        }
        CultivatorConfiguredPaths.basePatchedDirectory = new File(CultivatorConfiguredPaths.devDirectory.toString() + "/base")
        CultivatorTasks.applyBasePatchesTask = project.tasks.register("applyBasePatches", ApplyBasePatchesTask) {
            group = CultivatorBuildConstants.CULTIVATOR_TASK_GROUP
            decompilerOutputDirectory.set(CultivatorTasks.decompileJarTask.get().getDecompilerOutputPath())
            patchesEntry = "patches"
            patchedDirectory.set(CultivatorConfiguredPaths.basePatchedDirectory)
        }
        CultivatorTasks.compileMinecraftTask = project.tasks.register("compileMinecraft", JavaCompile) {
            source = project.fileTree(dir: CultivatorTasks.applyBasePatchesTask.get().getPatchedDirectory(), include: "**/*.java")
            destinationDirectory = new File(CultivatorConfiguredPaths.compileDirectory.toString() + "/build")
            classpath = project.configurations.named(CultivatorBuildConstants.MINECRAFT_DEPENDENCIES_CONFIGURATION_NAME).get()
            group = CultivatorBuildConstants.CULTIVATOR_TASK_GROUP
            options.compilerArgs << "-Xmaxerrs" << "9999999"
        }
        CultivatorConfiguredPaths.compileDirectory = new File(config.compileDir)
        CultivatorTasks.jarMinecraftTask = project.tasks.register("jarMinecraft", Jar) {
            group = CultivatorBuildConstants.CULTIVATOR_TASK_GROUP
            archiveBaseName = config.minecraftVersion
            destinationDirectory = new File(CultivatorConfiguredPaths.compileDirectory.toString() + "/bin")
            from(CultivatorTasks.compileMinecraftTask.get().getDestinationDirectory())
            include("**/*.class")
        }
        // [2.1.2] Register patch tasks
        CultivatorConfiguredPaths.patchDirectory = new File(config.patchDir)
        CultivatorConfiguredPaths.basePatchDirectory = new File(CultivatorConfiguredPaths.patchDirectory.toString() + "/base")
        CultivatorTasks.generateBasePatchesTask = project.tasks.register("generateBasePatches", GeneratePatchesTask) {
            group = CultivatorBuildConstants.CULTIVATOR_TASK_GROUP
            directoryA = CultivatorTasks.decompileJarTask.get().getDecompilerOutputPath()
            directoryB = CultivatorTasks.applyBasePatchesTask.get().getPatchedDirectory()
            outputDirectory = CultivatorConfiguredPaths.basePatchDirectory
        }
    }
}