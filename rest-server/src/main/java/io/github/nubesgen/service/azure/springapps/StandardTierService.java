package io.github.nubesgen.service.azure.springapps;

import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appcontainers.models.AppLogsConfiguration;
import com.azure.resourcemanager.appcontainers.models.LogAnalyticsConfiguration;
import com.azure.resourcemanager.appcontainers.models.ManagedEnvironment;
import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.appplatform.fluent.models.DeploymentResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.ServiceResourceInner;
import com.azure.resourcemanager.appplatform.models.ClusterResourceProperties;
import com.azure.resourcemanager.appplatform.models.SourceUploadedUserSourceInfo;
import com.azure.resourcemanager.appplatform.models.SpringAppDeployment;
import com.azure.resourcemanager.appplatform.models.SpringService;
import io.github.nubesgen.model.azure.springapps.DeploymentParameter;
import io.github.nubesgen.utils.AzureResourceManagerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

import static io.github.nubesgen.service.azure.springapps.Constants.Tier.StandardGen2;

@Service
public class StandardTierService implements ASAService {

    private final Logger log = LoggerFactory.getLogger(StandardTierService.class);

    private final SpringAppsService springAppsService;

    public StandardTierService(SpringAppsService springAppsService) {
        this.springAppsService = springAppsService;
    }

    /**
     * Provision spring service.
     *
     * @param deploymentManager ARM client
     * @param subscriptionId subscriptionId
     * @param resourceGroupName resourceGroupName
     * @param serviceName serviceName
     * @param region region
     * @param tier tier
     */
    public void provision(DeploymentManager deploymentManager, String subscriptionId, String resourceGroupName, String serviceName, String region, String tier) {
        try {
            deploymentManager.getAppPlatformManager().springServices().getByResourceGroup(resourceGroupName, serviceName);
            log.info("Spring service {} already exists.", serviceName);
        } catch (Exception e) {
            ServiceResourceInner serviceResourceInner = new ServiceResourceInner()
                    .withLocation(region)
                    .withSku(springAppsService.getSku(tier));

            if (Objects.equals(tier, StandardGen2)) {
                log.info("Creating container environment");
                String customerId = UUID.randomUUID().toString();
                String sharedKey = UUID.randomUUID().toString().replace("-", "");
                ContainerAppsApiManager containerAppsApiManager = deploymentManager.getContainerAppsApiManager();
                ManagedEnvironment managedEnvironment = containerAppsApiManager.managedEnvironments().define("cae-" + serviceName)
                        .withRegion(region)
                        .withExistingResourceGroup(resourceGroupName)
                        .withAppLogsConfiguration(
                                new AppLogsConfiguration()
                                        .withDestination("log-analytics")
                                        .withLogAnalyticsConfiguration(
                                                new LogAnalyticsConfiguration().withCustomerId(customerId).withSharedKey(sharedKey)))
                        .create();
                log.info("Create container environment {} completed", managedEnvironment.id());
                ClusterResourceProperties clusterResourceProperties = new ClusterResourceProperties();
                clusterResourceProperties.withManagedEnvironmentId(managedEnvironment.id());
                serviceResourceInner.withProperties(clusterResourceProperties);
            }
            deploymentManager.getAppPlatformManager().serviceClient().getServices().createOrUpdate(resourceGroupName, serviceName, serviceResourceInner);
        }
        log.info("Spring service {} created, tier: {}", serviceName, tier);
    }

    /**
     * Get build source log.
     *
     * @param deploymentManager ARM client
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @return null: no log generated, otherwise the build log
     */
    public String getBuildLogs(DeploymentManager deploymentManager, String subscriptionId,
                               String resourceGroupName,
                               String serviceName, String appName, String stage, String githubAction) {
        String buildLogs = null;
        try {
            SpringService springService = deploymentManager.getAppPlatformManager().springServices().getByResourceGroup(resourceGroupName, serviceName);
            SpringAppDeployment springAppDeployment = springService.apps().getByName(appName).getActiveDeployment();
            if (Objects.isNull(springAppDeployment) || Objects.isNull(springAppDeployment.getLogFileUrl())) {
                throw new IllegalStateException("Spring app deployment or deployment log file not exist - " + springAppDeployment);
            }
            String appStatus = springAppDeployment.innerModel().properties().provisioningState().toString();
            if(githubAction.equals("true") && !appStatus.equals("Updating")) {
                throw new IllegalStateException("App status is not updating");
            }
            buildLogs = springAppsService.getLogByUrl(springAppDeployment.getLogFileUrl(), null);
        } catch (Exception e) {
            log.error("Get build log failed, error: " + e.getMessage());
        }
        return buildLogs;
    }

    /**
     * Deploy source code to Azure Spring App instance.
     *
     * @param deploymentManager ARM client
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param deploymentParameter deployment parameter for different deployment type
     */
    public void deploy(DeploymentManager deploymentManager, String subscriptionId,
                               String resourceGroupName,
                               String serviceName,
                               String appName,
                               DeploymentParameter deploymentParameter) {
        log.info("Start build and deploy source code in Standard tier service {}.....", serviceName);
        try {
            SourceUploadedUserSourceInfo sourceInfo = new SourceUploadedUserSourceInfo();
            sourceInfo.withRuntimeVersion(deploymentParameter.getJavaVersion());
            sourceInfo.withRelativePath(deploymentParameter.getRelativePath());
            if (!Objects.equals(deploymentParameter.getModule(), "null")) {
                sourceInfo.withArtifactSelector(deploymentParameter.getModule()); // withTargetModule
            }
            String DEFAULT_DEPLOYMENT_NAME = "default";
            DeploymentResourceInner resourceInner = deploymentManager.getAppPlatformManager().serviceClient().getDeployments().get(resourceGroupName, serviceName, appName,
                    DEFAULT_DEPLOYMENT_NAME);
            resourceInner.properties().withSource(sourceInfo);
            deploymentManager.getAppPlatformManager().serviceClient().getDeployments().update(resourceGroupName, serviceName, appName,
                    DEFAULT_DEPLOYMENT_NAME, resourceInner);
             log.info("Deploy source code to app {} completed.", appName);
        } catch (Exception e) {
            throw new RuntimeException("Deploy source code to app " + appName + " failed, error: " + e.getMessage());
        }
    }

}
