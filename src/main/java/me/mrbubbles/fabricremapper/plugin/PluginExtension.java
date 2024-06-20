package me.mrbubbles.fabricremapper.plugin;

import java.io.File;

@SuppressWarnings("unused")
public class PluginExtension {
    private String mappingsVersion;
    private String modName;
    private File buildDir;
    private boolean replaceJar;

    public void setMappingsVersion(String mappingsVersion) {
        this.mappingsVersion = mappingsVersion;
    }

    public String getMappingsVersion() {
        return mappingsVersion;
    }

    public void setModName(String name, String version) {
        this.modName = name + "-" + version;
    }

    public String getModName() {
        return modName;
    }

    public void setbuildDir(File buildDir) {
        this.buildDir = buildDir;
    }

    public File getBuildDir() {
        return buildDir;
    }

    public void replaceJar(boolean replaceJar) {
        this.replaceJar = replaceJar;
    }

    public boolean isReplaceJar() {
        return replaceJar;
    }

}
