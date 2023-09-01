package io.github.nubesgen.service.azure.springapps;

import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.appplatform.fluent.models.AppResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.DeploymentResourceInner;
import com.azure.resourcemanager.appplatform.models.BuildResultUserSourceInfo;
import com.azure.resourcemanager.appplatform.models.DeploymentInstance;
import com.azure.resourcemanager.appplatform.models.DeploymentResourceProperties;
import com.azure.resourcemanager.appplatform.models.DeploymentSettings;
import com.azure.resourcemanager.appplatform.models.JarUploadedUserSourceInfo;
import com.azure.resourcemanager.appplatform.models.ResourceRequests;
import com.azure.resourcemanager.appplatform.models.ResourceUploadDefinition;
import com.azure.resourcemanager.appplatform.models.Sku;
import com.azure.resourcemanager.appplatform.models.SpringApp;
import com.azure.resourcemanager.appplatform.models.SpringAppDeployment;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.storage.file.share.ShareFileAsyncClient;
import com.azure.storage.file.share.ShareFileClientBuilder;
import io.github.nubesgen.utils.AzureResourceManagerUtils;
import io.github.nubesgen.utils.JavaProjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.azure.core.util.polling.implementation.PollingConstants.STATUS_RUNNING;
import static com.azure.core.util.polling.implementation.PollingConstants.STATUS_SUCCEEDED;
import static io.github.nubesgen.service.azure.springapps.Constants.DEFAULT_DEPLOYMENT_NAME;
import static io.github.nubesgen.service.azure.springapps.Constants.Enterprise;

/**
 * Basic operations for Azure Spring Apps
 */
public abstract class AbstractSpringAppsService implements SpringAppsService {

    private final Logger log = LoggerFactory.getLogger(AbstractSpringAppsService.class);
    private final SpringAppsLogService springAppsLogService;
    private final AzureResourceManagerService azureResourceManagerService;

    protected AbstractSpringAppsService(SpringAppsLogService springAppsLogService, AzureResourceManagerService azureResourceManagerService) {
        this.springAppsLogService = springAppsLogService;
        this.azureResourceManagerService = azureResourceManagerService;
    }

    /**
     * Check app exist.
     *
     * @param deploymentManager ARM client.
     * @param resourceGroupName the resource group name.
     * @param serviceName the service name.
     * @param appName the app name.
     * @return true if app exist, otherwise false
     */
    public boolean checkAppExist(DeploymentManager deploymentManager, String resourceGroupName,
                                 String serviceName,
                                 String appName) {
        try {
            AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();
            SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
            List<SpringApp> springApps = springService.apps().list().stream().filter(s -> s.name().equals(appName)).toList();
            return springApps.isEmpty();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Provision spring app.
     *
     * @param deploymentManager ARM client.
     * @param resourceGroupName the resource group name.
     * @param serviceName the service name.
     * @param appName the app name.
     */
    public void provisionSpringApp(DeploymentManager deploymentManager, String resourceGroupName,
                                   String serviceName,
                                   String appName) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();
        AppResourceInner appResourceInner = azureResourceManagerService.getAppResourceInner();
        appPlatformManager.serviceClient().getApps().createOrUpdate(resourceGroupName, serviceName, appName, appResourceInner);
        log.info("Provision spring app {} completed.", appName);
    }

    /**
     * Delete app.
     *
     * @param deploymentManager ARM client.
     * @param resourceGroupName the resource group name.
     * @param serviceName the service name.
     * @param appName the app name.
     */
    public void deleteApp(DeploymentManager deploymentManager,
                          String resourceGroupName,
                          String serviceName, String appName) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        springService.apps().deleteByName(appName);
        log.info("Delete app {} in service {} completed.", appName, serviceName);
    }

    /**
     * Create deployment for app.
     *
     * @param deploymentManager ARM client.
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param cpu the cpu
     * @param memory the memory
     * @param instanceCount the instance count
     */
    public void createDeploymentForApp(DeploymentManager deploymentManager,
                                       String resourceGroupName,
                                       String serviceName,
                                       String appName,
                                       String cpu,
                                       String memory,
                                       String instanceCount,
                                       Map<String, String> variables) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);

        DeploymentSettings deploymentSettings = new DeploymentSettings()
                .withResourceRequests(new ResourceRequests().withCpu(cpu).withMemory(memory));
        if (!variables.isEmpty()) {
            deploymentSettings.withEnvironmentVariables(variables);
        }

        DeploymentResourceProperties deploymentResourceProperties = new DeploymentResourceProperties();
        deploymentResourceProperties.withActive(true);
        deploymentResourceProperties.withDeploymentSettings(deploymentSettings);
        if (springService.sku().tier().equals(Enterprise)) {
            deploymentResourceProperties.withSource(new BuildResultUserSourceInfo().withBuildResultId("<default>"));
        } else {
            deploymentResourceProperties.withSource(new JarUploadedUserSourceInfo().withRelativePath("<default>"));
        }

        DeploymentResourceInner resourceInner = new DeploymentResourceInner()
                .withSku(
                        new Sku()
                                .withName(springService.sku().name())
                                // instance count
                                .withCapacity(Integer.valueOf(instanceCount)))
                .withProperties(deploymentResourceProperties);
        appPlatformManager.serviceClient().getDeployments().createOrUpdate(resourceGroupName, serviceName, appName, DEFAULT_DEPLOYMENT_NAME, resourceInner);
        log.info("Successfully created default deployment for app {}.", appName);
    }

    /**
     * Get upload url string.
     *
     * @param deploymentManager ARM client.
     * @param resourceGroupName the resource group name.
     * @param serviceName the service name.
     * @param appName the app name.
     * @return upload url.
     */
    public ResourceUploadDefinition getUploadUrl(DeploymentManager deploymentManager,
                                                 String resourceGroupName,
                                                 String serviceName,
                                                 String appName) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();
        ResourceUploadDefinition resourceUploadDefinition = appPlatformManager.serviceClient().getApps().getResourceUploadUrlAsync(
                resourceGroupName, serviceName, appName).block();
        log.info("Get upload url for app {} completed.", appName);
        return resourceUploadDefinition;
    }

    /**
     * Upload file.
     *
     * @param deploymentManager ARM client.
     * @param url the GitHub repository url.
     * @param branchName the branch name.
     * @param uploadUrl the upload url.
     */
    public void uploadFile(DeploymentManager deploymentManager, String uploadUrl,
                           String url,
                           String branchName) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();
        ShareFileAsyncClient fileClient = new ShareFileClientBuilder()
                .endpoint(uploadUrl)
                .httpClient(appPlatformManager.httpPipeline().getHttpClient())
                .buildFileAsyncClient();
        try {
            File gzFile = JavaProjectUtils.createTarGzFile(url, branchName);
            fileClient.create(gzFile.length())
                    .flatMap(fileInfo -> fileClient.uploadFromFile(gzFile.getAbsolutePath())).retry(2)
                    .block();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        log.info("Upload file to app completed.");
    }

    /**
     * Get application log.
     *
     * @param deploymentManager ARM client.
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @return none: no log generated, otherwise the application log
     */
    public String getApplicationLogs(DeploymentManager deploymentManager,
                                     String resourceGroupName,
                                     String serviceName, String appName, String status) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        String endpoint = springAppsLogService.getLogStreamingEndpoint(springService, appName);
        String url;
        try {
            if (!STATUS_SUCCEEDED.equals(status)) {
                url = String.format("%s?tailLines=%s&follow=%s", endpoint, 1000, true);
            } else {
                url = String.format("%s?tailLines=%s", endpoint, 1000);
            }
            final String basicAuth = AzureResourceManagerUtils.getAuthorizationCode(springService, appName);
            return springAppsLogService.getLogByUrl(url, basicAuth);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check deploy status.
     *
     * @param deploymentManager ARM client.
     * @param resourceGroupName the resource group name.
     * @param serviceName the service name.
     * @param appName the app name.
     * @return failed: Failed, succeed: Succeeded.
     */
    public String checkDeployStatus(DeploymentManager deploymentManager,
                                    String resourceGroupName,
                                    String serviceName, String appName) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();
        Map<String, String> appStatusMap = getAppAndInstanceStatus(appPlatformManager, resourceGroupName, serviceName, appName);
        String appStatus = appStatusMap.get("appStatus");
        String instanceStatus = appStatusMap.get("instanceStatus");
        //            build succeed, and deploy succeed
        if (STATUS_SUCCEEDED.equals(appStatus) && STATUS_RUNNING.equals(instanceStatus)) {
            return STATUS_SUCCEEDED;
        }
        return null;
    }

    /**
     * Get app status and instance status.
     *
     * @param appPlatformManager appPlatformManager
     * @param resourceGroupName resourceGroupName
     * @param serviceName serviceName
     * @param appName appName
     * @return app status and instance status
     */
    public Map<String, String> getAppAndInstanceStatus(AppPlatformManager appPlatformManager, String resourceGroupName,
                                                       String serviceName, String appName) {
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        SpringAppDeployment springAppDeployment = springService.apps().getByName(appName).getActiveDeployment();
        String appStatus = springAppDeployment.innerModel().properties().provisioningState().toString();

        List<DeploymentInstance> deploymentInstances = springAppDeployment.instances();
        deploymentInstances = deploymentInstances.stream().sorted(Comparator.comparing(DeploymentInstance::startTime)).collect(Collectors.toList());
        String instanceStatus = deploymentInstances.get(deploymentInstances.size() - 1).status();

        Map<String, String> appAndInstanceStatus = new HashMap<>();
        appAndInstanceStatus.put("appStatus", appStatus);
        appAndInstanceStatus.put("instanceStatus", instanceStatus);
        return appAndInstanceStatus;
    }

}
