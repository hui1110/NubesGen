package io.github.nubesgen.model;

public class AppInstance {

    private String id;

    private String name;

    private String runtimePlatform;

    private Double cpu;

    private Double memoryInGB;

    private Integer instanceCount;


    public AppInstance() {
    }

    public AppInstance(String id, String name, String runtimePlatform, Double cpu, Double memoryInGB,
                       Integer instanceCount) {
        this.id = id;
        this.name = name;
        this.runtimePlatform = runtimePlatform;
        this.cpu = cpu;
        this.memoryInGB = memoryInGB;
        this.instanceCount = instanceCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRuntimePlatform() {
        return runtimePlatform;
    }

    public void setRuntimePlatform(String runtimePlatform) {
        this.runtimePlatform = runtimePlatform;
    }

    public Double getCpu() {
        return cpu;
    }

    public void setCpu(Double cpu) {
        this.cpu = cpu;
    }

    public Double getMemoryInGB() {
        return memoryInGB;
    }

    public void setMemoryInGB(Double memoryInGB) {
        this.memoryInGB = memoryInGB;
    }

    public Integer getInstanceCount() {
        return instanceCount;
    }

    public void setInstanceCount(Integer instanceCount) {
        this.instanceCount = instanceCount;
    }

}
