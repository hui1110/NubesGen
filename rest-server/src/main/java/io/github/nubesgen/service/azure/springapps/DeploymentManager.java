package io.github.nubesgen.service.azure.springapps;

import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appplatform.AppPlatformManager;

public class DeploymentManager {

    private AppPlatformManager appPlatformManager;

    private ContainerAppsApiManager containerAppsApiManager;

    public DeploymentManager() {
    }

    public DeploymentManager(AppPlatformManager appPlatformManager) {
        this.appPlatformManager = appPlatformManager;
    }

    public DeploymentManager(AppPlatformManager appPlatformManager, ContainerAppsApiManager containerAppsApiManager) {
        this.appPlatformManager = appPlatformManager;
        this.containerAppsApiManager = containerAppsApiManager;
    }

    public AppPlatformManager getAppPlatformManager() {
        return appPlatformManager;
    }

    public void setAppPlatformManager(AppPlatformManager appPlatformManager) {
        this.appPlatformManager = appPlatformManager;
    }

    public ContainerAppsApiManager getContainerAppsApiManager() {
        return containerAppsApiManager;
    }

    public void setContainerAppsApiManager(ContainerAppsApiManager containerAppsApiManager) {
        this.containerAppsApiManager = containerAppsApiManager;
    }
}
