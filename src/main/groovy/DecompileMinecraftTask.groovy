import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.util.zip.ZipFile

abstract class DecompileMinecraftTask extends DefaultTask
{

    @Input
    abstract Property<FileCollection> getMinecraftLibraries()
    @InputFile
    abstract Property<File> getMinecraftJar()
    @OutputDirectory
    abstract Property<File> getDecompilerOutputPath()

    @TaskAction
    void decompile() {
        // [1] Decompile
        File minecraftJar = minecraftJar.get()
        File decompilerOutputPath = decompilerOutputPath.get()
        File[] libraries = minecraftLibraries.get()

        for(File lib : libraries) {
            logger.info(lib.toString())
        }

        MinecraftDecompiler decompiler = new MinecraftDecompiler(minecraftJar, decompilerOutputPath, libraries)
        decompiler.decompile()
        // [2] Explode (cache)
        File decompiledJar = new File(decompilerOutputPath.path, minecraftJar.name)
        ZipFile zipFile = new ZipFile(decompiledJar)
        for(entry in zipFile.entries()) {
            // We only care about java source files
            if (entry.isDirectory() || !entry.toString().endsWith(".java")) {
                continue
            }
            File extractedFile = new File(decompilerOutputPath, entry.name)
            if (!extractedFile.parentFile.exists()) {
                extractedFile.parentFile.mkdirs()
            }
            try (InputStream inputStream = zipFile.getInputStream(entry); FileOutputStream outputStream = new FileOutputStream(extractedFile)) {
                inputStream.transferTo(outputStream)
            }
        }
        zipFile.close()
        decompiledJar.delete()
    }

}