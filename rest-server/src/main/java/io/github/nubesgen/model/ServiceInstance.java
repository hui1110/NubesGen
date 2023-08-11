package io.github.nubesgen.model;

import com.azure.core.management.Region;

public class ServiceInstance {

    private Region region;
    private String resourceGroupName;
    private String id;
    private String name;
    private String sku;

    public ServiceInstance(Region region, String resourceGroupName, String id, String name, String sku) {
        this.region = region;
        this.resourceGroupName = resourceGroupName;
        this.id = id;
        this.name = name;
        this.sku = sku;
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

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getResourceGroupName() {
        return resourceGroupName;
    }

    public void setResourceGroupName(String resourceGroupName) {
        this.resourceGroupName = resourceGroupName;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

}
