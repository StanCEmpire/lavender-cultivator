import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class DownloadClientJarTask extends DefaultTask
{
    @Input
    abstract Property<String> getDownloadUrl()

    @OutputFile
    abstract Property<File> getOutputFile()

    DownloadClientJarTask() {
    }

    @TaskAction
    void downloadClientJar() {
        UrlUtils.downloadFromUrl(downloadUrl.get(), getOutputFile().get())
    }
}
