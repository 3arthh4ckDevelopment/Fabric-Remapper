package me.mrbubbles.fabricremapper;

import me.mrbubbles.fabricremapper.plugin.RemapperPlugin;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import org.gradle.api.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class Main {

    private static final Logger LOGGER = RemapperPlugin.getLogger();

    public static boolean isPathValid(Path path) {
        try {
            return isPathUsable(path) && Files.exists(path) && Files.isReadable(path) && Files.isRegularFile(path) && Files.size(path) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isPathUsable(Path path) {
        return path != null && !path.toString().isEmpty();
    }

    public static boolean isJar(Path path) {
        if (!isPathValid(path)) return false;

        String name = path.getFileName().toString().toLowerCase();

        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    public static boolean isValidMappingsVersion(String mappingsVersion) {
        return mappingsVersion != null && !mappingsVersion.isEmpty() && mappingsVersion.matches("^\\d+\\.\\d+\\.\\d+\\+build\\.\\d+$");
    }

    public static void remap(Path input, Path output, String mappingsVersion, Path tempDir) {
        if (!isJar(input)) {
            LOGGER.error("Input is invalid! Please give a valid input.");
            return;
        } else if (!isPathUsable(output)) {
            LOGGER.error("Output is invalid! Please give a valid output.");
            return;
        } else if (!isValidMappingsVersion(mappingsVersion)) {
            LOGGER.error("Mappings version is invalid! Please give a valid mappings version.");
        }

        Path mappingsPath = YarnDownloading.resolve(mappingsVersion, tempDir);
        LOGGER.info(String.valueOf(mappingsPath.toAbsolutePath()));
        String outputName = output.getFileName().toString();
        int lastIndex = outputName.lastIndexOf('.');

        output = output.resolveSibling(lastIndex == -1 ? outputName + ".jar" : outputName.substring(0, lastIndex) + ".jar");

        if (output.toFile().exists()) output.toFile().delete();

        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createTinyMappingProvider(mappingsPath, "intermediary", "named"))
                .renameInvalidLocals(true)
                .rebuildSourceFilenames(true)
                .ignoreConflicts(true)
                .keepInputData(true)
                .skipLocalVariableMapping(true)
                .ignoreFieldDesc(true)
                .build();

        try {
            OutputConsumerPath outputConsumer = new OutputConsumerPath(output);
            outputConsumer.addNonClassFiles(input);
            remapper.readInputs(input);

            remapper.readClassPath(input);
            remapper.apply(outputConsumer);
            remapper.finish();

            outputConsumer.close();
        } catch (IOException e) {
            LOGGER.error("Error during remapping: " + e.getMessage());
        }

        Path mappingsTiny2 = YarnDownloading.resolveTiny2(mappingsVersion, tempDir);

        try {
            Map<String, String> mapping = RemapUtil.getMappings(mappingsTiny2);

            RemapUtil.remapJar(output, remapper, mapping);
        } catch (IOException e) {
            LOGGER.error("Error during obtaining Tiny v2 mappings: " + e.getMessage());
        }

        try {
            Files.delete(mappingsPath);
            if (mappingsTiny2 != null) {
                Files.delete(mappingsTiny2);
            }
            Files.delete(YarnDownloading.path);
        } catch (IOException e) {
            LOGGER.error("Error during deleting mappings: " + e.getMessage());
        }

        LOGGER.info("Finished remapping '" + input.toFile().getName() + "'!");
    }

    public static String getMinecraftVersion(Path jarPath) {
        String minecraftVersion = null;

        try {
            JarFile jarFile = new JarFile(jarPath.toFile());

            ZipEntry jsonEntry = jarFile.getEntry("fabric.mod.json");

            if (jsonEntry != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(jarFile.getInputStream(jsonEntry)));

                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.contains("\"minecraft\"")) {
                        minecraftVersion = line.split(":")[1].replace("\"", "").replace(",", "").trim();
                        break;
                    }
                }

                reader.close();
            }

            jarFile.close();
        } catch (IOException e) {
            LOGGER.error("Error during getting the Minecraft version automatically: " + e.getMessage(), true);
        }

        return minecraftVersion;
    }
}