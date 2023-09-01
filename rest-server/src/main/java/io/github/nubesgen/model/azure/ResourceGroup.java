package io.github.nubesgen.model.azure;

public class ResourceGroup {

    private String name;

    public ResourceGroup(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
