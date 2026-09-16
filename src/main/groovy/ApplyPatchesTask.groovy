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

abstract class ApplyPatchesTask extends DefaultTask
{
    @InputDirectory
    abstract Property<File> getBaseDirectory()
    @InputDirectory
    abstract Property<File> getPatchesDirectory()
    @OutputDirectory
    abstract Property<File> getPatchedDirectory()

    @TaskAction
    void applyPatches() {
        File baseDirectory = baseDirectory.get()
        File patchesDirectory = patchesDirectory.get()
        File patchedDirectory = patchedDirectory.get()
        List<Path> basePaths = Files.walk(baseDirectory.toPath()).toList()
        // Iterate through each path and apply the patches
        for(basePath in basePaths)
        {
            // Skip directories (these don't need patching)
            if (basePath.toFile().isDirectory()) {
                continue
            }
            // Get relative path in the base directory
            Path relativePath = baseDirectory.toPath().relativize(basePath)
            Path patchedPath = patchedDirectory.toPath().resolve(relativePath)

            // Get patch file (if it exists)
            List<String> baseFileLines = Files.readAllLines(basePath)
            Path patchFilePath = patchesDirectory.toPath().resolve(relativePath.toString() + ".patch")
            // Output the base file as-is to both cache and output if there is no patch
            List<String> patchedLines = baseFileLines
            if(patchFilePath.toFile().exists())
            {
                List<String> patchLines = Files.readAllLines(patchFilePath)
                Patch<String> patch  = UnifiedDiffUtils.parseUnifiedDiff(patchLines)
                patchedLines = DiffUtils.patch(baseFileLines, patch)
            }

            // Patch file and write to disk
            if(!patchedPath.parent.toFile().exists())
            {
                patchedPath.parent.toFile().mkdirs()
            }
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(patchedPath.toString()))
            patchedLines.forEach(patchedLine -> {
                bufferedWriter.writeLine(patchedLine)
            })
            bufferedWriter.close()
        }
    }
}
