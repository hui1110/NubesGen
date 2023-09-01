package io.github.nubesgen.service.azure.springapps;

import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.loganalytics.LogAnalyticsManager;

/**
 * Providing operations for getting Azure Resource Manager or Container Apps API Manager.
 */
public class DeploymentManager {

    private AppPlatformManager appPlatformManager;

    private ContainerAppsApiManager containerAppsApiManager;

    private LogAnalyticsManager logAnalyticsManager;

    private AzureResourceManager azureResourceManager;

    public DeploymentManager() {
    }

    public DeploymentManager(AppPlatformManager appPlatformManager) {
        this.appPlatformManager = appPlatformManager;
    }

    public DeploymentManager(AzureResourceManager azureResourceManager) {
        this.azureResourceManager = azureResourceManager;
    }

    public DeploymentManager(AppPlatformManager appPlatformManager, ContainerAppsApiManager containerAppsApiManager, LogAnalyticsManager logAnalyticsManager) {
        this.appPlatformManager = appPlatformManager;
        this.containerAppsApiManager = containerAppsApiManager;
        this.logAnalyticsManager = logAnalyticsManager;
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


    public LogAnalyticsManager getLogAnalyticsManager() {
        return logAnalyticsManager;
    }

    public void setLogAnalyticsManager(LogAnalyticsManager logAnalyticsManager) {
        this.logAnalyticsManager = logAnalyticsManager;
    }

    public AzureResourceManager getAzureResourceManager() {
        return azureResourceManager;
    }

    public void setAzureResourceManager(AzureResourceManager azureResourceManager) {
        this.azureResourceManager = azureResourceManager;
    }

}
