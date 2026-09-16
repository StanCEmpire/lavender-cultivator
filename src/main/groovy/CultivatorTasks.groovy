import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

class CultivatorTasks {
    static TaskProvider<DownloadClientJarTask> downloadJarTask
    static TaskProvider<DecompileMinecraftTask> decompileJarTask
    static TaskProvider<ApplyBasePatchesTask> applyBasePatchesTask
    static TaskProvider<JavaCompile> compileMinecraftTask
    static TaskProvider<Jar> jarMinecraftTask
    static TaskProvider<GeneratePatchesTask> generateBasePatchesTask
}
