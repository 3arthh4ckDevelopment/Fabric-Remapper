package me.mrbubbles.fabricremapper.plugin;

import me.mrbubbles.fabricremapper.Main;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.nio.file.FileSystems;
import java.nio.file.Paths;

public class RemapperPlugin implements Plugin<Project> {

    private static Logger LOGGER;

    public static Logger getLogger() {
        return LOGGER;
    }

    @Override
    public void apply(Project project) {
        LOGGER = project.getLogger();
        PluginExtension extension = project.getExtensions().create("remapJarToIntermediary", PluginExtension.class);

        project.task("remapJarToIntermediary").doLast(task -> {
            String baseIOPath = String.join(FileSystems.getDefault().getSeparator(),
                    extension.getBuildDir().getAbsolutePath(), "libs", extension.getModName());

            try {
                Main.remap(
                        Paths.get(baseIOPath + ".jar"),
                        Paths.get(baseIOPath + (extension.isReplaceJar() ? ".jar" : "-remapped.jar")),
                        extension.getMappingsVersion(),
                        Paths.get(extension.getBuildDir().getAbsolutePath(), "tmp"));
            } catch (Exception e) {
                LOGGER.error("An error occurred while remapping", e);
            }
        });
    }
}