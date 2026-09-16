import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.Patch
import com.github.difflib.patch.PatchFailedException
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.stream.Stream
import java.util.zip.ZipEntry

abstract class ApplyBasePatchesTask extends DefaultTask
{
    @Input
    abstract Property<String> getPatchesEntry()
    @InputDirectory
    abstract Property<File> getDecompilerOutputDirectory()
    @OutputDirectory
    abstract Property<File> getPatchedDirectory()

    @TaskAction
    void applyPatches() {
        //Get Input and output
        String patchesEntry = patchesEntry.get()
        // Sanity check the input
        if(!patchesEntry.matches("^(\\w|\\d)+\$"))
        {
            throw new IllegalArgumentException(
                    "Patch entry '%s' is not a valid entry name (must be alphanumeric + underscores)".formatted(patchesEntry)
            )
        }
        File patchedDirectory = patchedDirectory.get()
        File decompilerOutputDirectory = decompilerOutputDirectory.get()

        // Get current this jar file
        URI jarUri = getClass().protectionDomain.codeSource.location.toURI()
        JarFile jarFile = new JarFile(new File(jarUri))
        // Walk the decompiler directory and look for any corresponding patch
        // files. Apply patch if necessary and write to output
        List<Path> paths = Files.walk(decompilerOutputDirectory.toPath()).toList()
        for(path in paths)
        {
            if(path.toFile().isDirectory() || !path.toString().endsWith(".java"))
            {
                continue
            }
            Path relativePath = decompilerOutputDirectory.toPath().relativize(path)
            List<String> baseFileLines = Files.readAllLines(path)
            ZipEntry patchEntry = jarFile.getEntry("patches/" + relativePath.toString().replace('\\', '/') + ".patch")
            // Output the base file as-is to both cache and output if there is no patch
            List<String> patchedLines = baseFileLines
            if(patchEntry != null)
            {
                List<String> patchLines = jarFile.getInputStream(patchEntry).readLines()
                Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchLines)
                patchedLines = DiffUtils.patch(baseFileLines, patch)
            }
            Path patchedFilePath = patchedDirectory.toPath().resolve(relativePath)
            if(!patchedFilePath.parent.toFile().exists())
            {
                patchedFilePath.parent.toFile().mkdirs()
            }
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(patchedFilePath.toString()))
            patchedLines.forEach(patchedLine -> {
                bufferedWriter.writeLine(patchedLine)
            })
            bufferedWriter.close()
        }
    }
}
