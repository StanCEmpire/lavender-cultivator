import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.Patch
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

abstract class GeneratePatchesTask extends DefaultTask
{
    @InputDirectory
    abstract Property<File> getDirectoryA()
    @InputDirectory
    abstract Property<File> getDirectoryB()
    @OutputDirectory
    abstract Property<File> getOutputDirectory()

    GeneratePatchesTask() {
        group = "MinecraftDev"
    }

    @TaskAction
    void generatePatches() {

        File directoryA = directoryA.get()
        File directoryB = directoryB.get()
        File outputDirectory = outputDirectory.get()
        Stream<Path> paths = Files.walk(directoryA.toPath())
        paths.forEach(pathA -> {
            // Skip any directories
            if (pathA.toFile().isDirectory()) {
                return
            }
            // Get path from cache and development directory
            Path relativePath = directoryA.toPath().relativize(pathA)
            Path pathB = directoryB.toPath().resolve(relativePath)
            List<String> a = Files.readAllLines(pathA)
            List<String> b = Files.readAllLines(pathB)
            Patch<String> patch = DiffUtils.diff(a, b);
            // If there are no deltas, don't generate a patch file
            if (patch.getDeltas().isEmpty()) {
                return
            }
            // Generate unified diff
            List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
                    "a/" + relativePath.toString().replace("\\", "/"),
                    "b/" + relativePath.toString().replace("\\", "/"),
                    a,
                    patch,
                    0
            )
            // Write diff
            String patchFilePath = outputDirectory.toPath().resolve(relativePath).toString() + ".patch"
            File patchFile = new File(patchFilePath)
            if (!patchFile.parentFile.exists()) {
                patchFile.parentFile.mkdirs()
            }
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(patchFile))
            unifiedDiff.forEach(diffLine -> {
                bufferedWriter.writeLine(diffLine)
            })
            bufferedWriter.close()
        })
        paths.close()

    }

}
