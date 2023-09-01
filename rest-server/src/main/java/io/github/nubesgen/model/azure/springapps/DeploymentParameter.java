package io.github.nubesgen.model.azure.springapps;

public class DeploymentParameter {

    // for JAR deployment
    private String module;
    private String javaVersion;
    private String relativePath;

    // for BuildService deployment
    private String buildId;

    public DeploymentParameter() {
    }

    public static DeploymentParameter buildJarParameters(String module, String javaVersion, String relativePath) {
        return new DeploymentParameter(module, javaVersion, relativePath);
    }

    public static DeploymentParameter buildBuildResultParameters(String buildId) {
        return new DeploymentParameter(buildId);
    }

    public DeploymentParameter(String buildId) {
        this.buildId = buildId;
    }

    public DeploymentParameter(String module, String javaVersion, String relativePath) {
        this.module = module;
        this.javaVersion = javaVersion;
        this.relativePath = relativePath;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getBuildId() {
        return buildId;
    }

    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }

}
