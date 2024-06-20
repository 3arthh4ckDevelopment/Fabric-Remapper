package me.mrbubbles.fabricremapper;

import me.mrbubbles.fabricremapper.plugin.RemapperPlugin;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.gradle.api.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class RemapUtil {

    private static final Logger LOGGER = RemapperPlugin.getLogger();

    public static void remapJar(Path outputJar, TinyRemapper remapper, Map<String, String> mappings) throws IOException {
        Path tempOutputJar = Paths.get(outputJar.toString() + "_temp");

        try (ZipInputStream inputZip = new ZipInputStream(Files.newInputStream(outputJar));
             ZipOutputStream tempOutputZip = new ZipOutputStream(Files.newOutputStream(tempOutputJar))) {

            ZipEntry entry;
            while ((entry = inputZip.getNextEntry()) != null) {
                String entryName = entry.getName();

                if (!entryName.endsWith(".class")) {
                    tempOutputZip.putNextEntry(new ZipEntry(entryName));
                    byte[] buffer = readStream(inputZip);
                    if (entryName.endsWith(".accesswidener")) {
                        buffer = remapAccessWidener(buffer, remapper.getRemapper(), mappings);
                    }
                    tempOutputZip.write(buffer);
                    tempOutputZip.closeEntry();
                    continue;
                }

                byte[] remappedBytes = remapClass(readStream(inputZip), mappings);

                tempOutputZip.putNextEntry(new ZipEntry(entryName));
                tempOutputZip.write(remappedBytes);
                tempOutputZip.closeEntry();
            }
        }

        Files.move(tempOutputJar, outputJar, StandardCopyOption.REPLACE_EXISTING);
    }

    private static byte[] readStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    private static byte[] remapAccessWidener(byte[] accessWidenerBytes, Remapper remapper, Map<String, String> mappings) {
        AccessWidenerWriter writer = new AccessWidenerWriter();
        AccessWidenerRemapper remappingDecorator = new AccessWidenerRemapper(writer, remapper, "intermediary", "named");
        AccessWidenerReader accessWidenerReader = new AccessWidenerReader(remappingDecorator);
        accessWidenerReader.read(accessWidenerBytes, "intermediary");

        byte[] newAccessWidenerBytes = writer.write();
        String accessWidener = new String(newAccessWidenerBytes, StandardCharsets.UTF_8);
        String[] lines = accessWidener.split("\n");
        StringBuilder result = new StringBuilder();

        result.append(lines[0].replace('\t', ' ')).append("\n\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            String[] parts = line.split("\t");
            for (String part : parts) {
                if (mappings.containsKey(part)) {
                    line = line.replace(part, mappings.get(part));
                }
            }

            result.append(line.replace('\t', ' ')).append("\n");
        }

        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] remapClass(byte[] classBytes, Map<String, String> mappings) {
        ClassReader classReader = new ClassReader(classBytes);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);

        if (mappings.containsKey(classNode.name)) {
            LOGGER.info("Found nasty class. Renaming \"" + classNode.name + "\" to \"" + mappings.get(classNode.name) + "\"");
            classNode.name = mappings.get(classNode.name);
        }

        classNode.fields.parallelStream().filter(fieldNode -> mappings.containsKey(fieldNode.name)).forEach(fieldNode -> {
            LOGGER.info("Found nasty field. Renaming \"" + fieldNode.name + "\" to \"" + mappings.get(fieldNode.name) + "\"");
            fieldNode.name = mappings.get(fieldNode.name);
        });

        classNode.methods.parallelStream().filter(methodNode -> mappings.containsKey(methodNode.name)).forEach(methodNode -> {
            LOGGER.info("Found nasty method. Renaming \"" + methodNode.name + "\" to \"" + mappings.get(methodNode.name) + "\"");
            methodNode.name = mappings.get(methodNode.name);
        });

        for (MethodNode method : classNode.methods) {
            InsnList list = method.instructions;
            Arrays.stream(list.toArray()).parallel().forEach(currentInsn -> {
                if (currentInsn instanceof MethodInsnNode methodInsnNode) {

                    String ownerName = methodInsnNode.owner.substring(methodInsnNode.owner.lastIndexOf('/') + 1);
                    if (mappings.containsKey(ownerName)) {
                        String ownerDir = methodInsnNode.owner.substring(0, methodInsnNode.owner.lastIndexOf('/'));
                        LOGGER.info("Found nasty class call. Renaming \"" + ownerName + "\" to \"" + mappings.get(ownerName) + "\"");
                        methodInsnNode.owner = ownerDir + mappings.get(ownerName);
                    }

                    if (mappings.containsKey(methodInsnNode.name)) {
                        LOGGER.info("Found nasty method call. Renaming \"" + methodInsnNode.name + "\" to \"" + mappings.get(methodInsnNode.name) + "\"");
                        methodInsnNode.name = mappings.get(methodInsnNode.name);
                    }
                } else if (currentInsn instanceof FieldInsnNode fieldInsnNode) {

                    String ownerName = fieldInsnNode.owner.substring(fieldInsnNode.owner.lastIndexOf('/') + 1);
                    if (mappings.containsKey(ownerName)) {
                        String ownerDir = fieldInsnNode.owner.substring(0, fieldInsnNode.owner.lastIndexOf('/'));
                        LOGGER.info("Found nasty class call. Renaming \"" + ownerName + "\" to \"" + mappings.get(ownerName) + "\"");
                        fieldInsnNode.owner = ownerDir + mappings.get(ownerName);
                    }

                    if (mappings.containsKey(fieldInsnNode.name)) {
                        LOGGER.info("Found nasty field call. Renaming \"" + fieldInsnNode.name + "\" to \"" + mappings.get(fieldInsnNode.name) + "\"");
                        fieldInsnNode.name = mappings.get(fieldInsnNode.name);
                    }
                } else if (currentInsn instanceof InvokeDynamicInsnNode dynamicInsnNode) {

                    if (mappings.containsKey(dynamicInsnNode.name)) {
                        LOGGER.info("Found nasty invoke dynamic. Renaming \"" + dynamicInsnNode.name + "\" to \"" + mappings.get(dynamicInsnNode.name) + "\"");
                        dynamicInsnNode.name = mappings.get(dynamicInsnNode.name);
                    }

                    if (mappings.containsKey(dynamicInsnNode.bsm.getName())) {
                        LOGGER.info("Found nasty bsm. Renaming \"" + dynamicInsnNode.bsm.getName() + "\" to \"" + mappings.get(dynamicInsnNode.bsm.getName()) + "\"");
                        Handle handle = dynamicInsnNode.bsm;
                        InvokeDynamicInsnNode newDynamicInsnNode = new InvokeDynamicInsnNode(dynamicInsnNode.name, dynamicInsnNode.desc,
                                new Handle(handle.getTag(), handle.getOwner(), mappings.get(dynamicInsnNode.bsm.getName()), handle.getDesc()), dynamicInsnNode.bsmArgs);
                        method.instructions.insertBefore(currentInsn, newDynamicInsnNode);
                        method.instructions.remove(currentInsn);
                    }
                } else if (currentInsn instanceof LdcInsnNode ldcInsnNode && ((LdcInsnNode) currentInsn).cst instanceof String) {
                    String cst = (String) ldcInsnNode.cst;

                    if (mappings.containsKey(cst)) {
                        LOGGER.info("Found nasty string constant. Renaming \"" + cst + "\" to \"" + mappings.get(cst) + "\"");
                        ldcInsnNode.cst = mappings.get(cst);
                    }
                }
            });
        }

        classNode.accept(classWriter);

        return classWriter.toByteArray();
    }

    public static Map<String, String> getMappings(Path path) throws IOException {
        Map<String, String> map = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8));

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.replaceAll("\\s+", " ");
            if (line.startsWith(" ")) line = line.substring(1);

            String[] lines = line.split(" ");


            if (lines.length < 4 || lines[0].startsWith("c") || lines[0].startsWith("p") || lines[3].startsWith("<") || lines[2].equals(lines[3]))
                continue;

            map.put(lines[2], lines[3]);
        }

        reader.close();

        return map;
    }

}
