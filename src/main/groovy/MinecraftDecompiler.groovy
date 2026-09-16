import org.jetbrains.java.decompiler.main.DecompilerContext
import org.jetbrains.java.decompiler.main.Fernflower
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger
import org.jetbrains.java.decompiler.main.extern.IResultSaver

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class MinecraftDecompiler implements IResultSaver {

    private Fernflower ffEngine
    private final File minecraftJar
    private final File destination
    private final File[] libraries
    private final Map<String, ZipOutputStream> mapArchiveStreams
    private final Map<String, Set<String>> mapArchiveEntries

    MinecraftDecompiler(File minecraftJar, File destination, File[] libraries) {
        PrintStreamLogger logger = new PrintStreamLogger(System.out)
        Map<String, Object> options = new HashMap<>()
        options.put("uto", 0) // Do not consider nameless types as Object
        options.put("iec", 1) // Include entire classpath
        options.put("dgs", 1) // Decompile generic signatures
        options.put("decompile-assert", 0) // Do not decompile assertions
        options.put("remove-synthetic", 1) // Remove synthetic members
        options.put("log", "INFO") // Set log level
        ffEngine = new Fernflower(this, options, logger)
        this.minecraftJar = minecraftJar
        this.destination = destination
        this.libraries = libraries
        this.mapArchiveStreams = new HashMap<>()
        this.mapArchiveEntries = new HashMap<>()
    }

    boolean decompile() {

        ffEngine.addSource(minecraftJar)

        for (File library : libraries) {
            ffEngine.addLibrary(library)
        }

        try {
            ffEngine.decompileContext()
        }
        finally {
            ffEngine.clearContext()
        }

        return true

    }

    @Override
    void saveFolder(String path) {
        // Attempt to create directories
        File directory = new File(getAbsolutePath(path))
        boolean directoriesMade = directory.mkdirs()
        // If `mkdirs` failed or the path is not a directory then fail
        if(!(directoriesMade || directory.isDirectory())) {
            throw new RuntimeException("Failed to create directory '%s' !".formatted(absolutePath))
        }
    }

    @Override
    void copyFile(String source, String path, String entryName) {
        File sourceFile = new File(source)
        File destinationFile = new File(getAbsolutePath(path), entryName)

        try(FileInputStream inputStream = new FileInputStream(sourceFile); FileOutputStream outputStream = new FileOutputStream(destinationFile)) {
            inputStream.transferTo(outputStream)
        }
    }

    @Override
    void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
        File file = new File(getAbsolutePath(path), entryName)
        try(Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            out.write(content)
        }
    }

    @Override
    void createArchive(String path, String archiveName, Manifest manifest) {
        File archiveFile = new File(getAbsolutePath(path), archiveName)
        try {
            boolean fileCreated = archiveFile.createNewFile()
            // If archive was not created or is not a file then fail
            if (!(fileCreated || archiveFile.isFile())) {
                throw new IOException("File '%s' could not be created!".formatted(archiveFile.absolutePath))
            }

            FileOutputStream archiveOutputStream = new FileOutputStream(archiveFile)
            ZipOutputStream zipOutputStream = new ZipOutputStream(archiveOutputStream)
            if(manifest != null) {
                ZipEntry manifestEntry = new ZipEntry(JarFile.MANIFEST_NAME)
                Instant now = Instant.now()
                manifestEntry.setTime(now.toEpochMilli())
                zipOutputStream.putNextEntry(manifestEntry)
                manifest.write(zipOutputStream)
                zipOutputStream.closeEntry()
            }
            mapArchiveStreams.put(archiveFile.getPath(), zipOutputStream)
        }
        catch (IOException exception) {
            DecompilerContext.getLogger().writeMessage("Could not create archive '%s'!".formatted(archiveFile.toString()), exception)
        }
    }

    @Override
    void saveDirEntry(String path, String archiveName, String entryName) {
        saveClassEntry(path, archiveName, null, entryName, null)
    }

    @Override
    void copyEntry(String source, String path, String archiveName, String entryName) {
        String filePath = new File(getAbsolutePath(path), archiveName).getPath()

        if (!checkEntry(entryName, filePath) || !entryName.endsWith(".class")) {
            return
        }

        try(ZipFile srcArchive = new ZipFile(new File(source))) {
            ZipEntry zipEntry = srcArchive.getEntry(entryName)
            if (zipEntry != null) {
                try (InputStream inputStream = srcArchive.getInputStream(zipEntry)) {
                    ZipOutputStream outputStream = mapArchiveStreams.get(filePath)
                    final ZipEntry newZipEntry = new ZipEntry(entryName)
                    newZipEntry.setTime(zipEntry.getTime())
                    outputStream.putNextEntry(newZipEntry)
                    inputStream.transferTo(outputStream)
                }
            }
        }
        catch (IOException exception) {
            DecompilerContext.getLogger().writeMessage("Could not copy zip entry '%s' from archive '%s' to archive '%s'".formatted(entryName, source, filePath), exception)
        }
    }

    @Override
    void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
        String filePath = new File(getAbsolutePath(path), archiveName).getPath()

        if(!checkEntry(entryName, filePath)) {
            return
        }

        try {
            ZipOutputStream out = mapArchiveStreams.get(filePath)
            ZipEntry zipEntry = new ZipEntry(entryName)
            Instant now = Instant.now()
            zipEntry.setTime(now.toEpochMilli())
            out.putNextEntry(zipEntry)
            if (content != null) {
                out.write(content.getBytes(StandardCharsets.UTF_8))
            }
        }
        catch (IOException exception) {
            DecompilerContext.getLogger().writeMessage("Could not create zip entry '%s' in archive '%s'".formatted(entryName, filePath), exception)
        }
    }

    @Override
    void closeArchive(String path, String archiveName) {
        String file = new File(getAbsolutePath(path), archiveName).getPath()
        try {
            mapArchiveEntries.remove(file)
            mapArchiveStreams.remove(file).close()
        }
        catch(IOException exception) {
            DecompilerContext.getLogger().writeMessage("Could not close archive '%s'!".formatted(file), exception)
        }
    }

    private String getAbsolutePath(String path) {
        return new File(this.destination, path).absolutePath
    }

    private boolean checkEntry(String entryName, String filePath) {
        Set<String> set = mapArchiveEntries.computeIfAbsent(filePath, k -> new HashSet<>())

        boolean added = set.add(entryName)
        if (!added) {
            DecompilerContext.getLogger().writeMessage("Archive entry '%s' already exists in '%s'".formatted(entryName, filePath), IFernflowerLogger.Severity.WARN)
        }
        return added
    }
}
