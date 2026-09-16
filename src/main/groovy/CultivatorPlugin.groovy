import org.gradle.api.Plugin
import org.gradle.api.Project

class CultivatorPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        def logger = project.logger

        // Add maven central
        project.getRepositories().mavenCentral()
        project.getRepositories().maven {
            url="https://libraries.minecraft.net"
        }

        // Create Extension
        project.getExtensions().create("cultivator", CultivatorExtension.class, project)

    }

}