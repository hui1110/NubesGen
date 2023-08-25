package io.github.nubesgen.service;

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
import io.github.nubesgen.utils.ASADeployUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.azure.core.util.polling.implementation.PollingConstants.STATUS_FAILED;
import static com.azure.core.util.polling.implementation.PollingConstants.STATUS_RUNNING;

@Service
public class ASAStandardTierService implements ASAService {

    private final Logger log = LoggerFactory.getLogger(ASAStandardTierService.class);

    /**
     * Provision spring service.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId subscriptionId
     * @param resourceGroupName resourceGroupName
     * @param serviceName serviceName
     * @param region region
     * @param tier tier
     */
    public void provisionSpringService(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName, String serviceName, String region, String tier) {
        AppPlatformManager appPlatformManager = ASADeployUtils.getAppPlatformManager(management, subscriptionId);
        try {
            appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
            log.info("Spring service {} already exists.", serviceName);
        } catch (Exception e) {
            ServiceResourceInner serviceResourceInner = new ServiceResourceInner()
                    .withLocation(region)
                    .withSku(ASADeployUtils.getSku(tier));

            if (Objects.equals(tier, "StandardGen2")) {
                log.info("Creating container environment");
                String customerId = UUID.randomUUID().toString();
                String sharedKey = UUID.randomUUID().toString().replace("-", "");
                ContainerAppsApiManager containerAppsApiManager = ASADeployUtils.getContainerAppsApiManager(management, subscriptionId);
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
            appPlatformManager.serviceClient().getServices().createOrUpdate(resourceGroupName, serviceName, serviceResourceInner);
        }
        log.info("Spring service {} created, tier: {}", serviceName, tier);
    }

    /**
     * Get build source log.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @return null: no log generated, otherwise the build log
     */
    public String getBuildLogs(OAuth2AuthorizedClient management, String subscriptionId,
                               String resourceGroupName,
                               String serviceName, String appName, String stage, String githubAction) {
        String buildLogs = null;
        try {
            AppPlatformManager appPlatformManager = ASADeployUtils.getAppPlatformManager(management, subscriptionId);
            SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
            SpringAppDeployment springAppDeployment = springService.apps().getByName(appName).getActiveDeployment();
            if (Objects.isNull(springAppDeployment) || Objects.isNull(springAppDeployment.getLogFileUrl())) {
                return null;
            }
            String appStatus = springAppDeployment.innerModel().properties().provisioningState().toString();
            if(githubAction.equals("true") && !appStatus.equals("Updating")) {
                return null;
            }
            buildLogs = ASADeployUtils.getLogByUrl(springAppDeployment.getLogFileUrl(), null);
        } catch (Exception e) {
            log.error("Get build log failed, error: " + e.getMessage());
        }
        return buildLogs;
    }

    /**
     * Deploy source code to Azure Spring App instance.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param javaVersion the java version
     * @param relativePath the relative path
     */
    public void buildAndDeploySourceCode(OAuth2AuthorizedClient management, String subscriptionId,
                               String resourceGroupName,
                               String serviceName,
                               String appName,
                               String module, String javaVersion,
                               String relativePath) {
        log.info("Start build and deploy source code in Standard tier service {}.....", serviceName);
        try {
            SourceUploadedUserSourceInfo sourceInfo = new SourceUploadedUserSourceInfo();
            sourceInfo.withRuntimeVersion(javaVersion);
            sourceInfo.withRelativePath(relativePath);
            if (!Objects.equals(module, "null")) {
                sourceInfo.withArtifactSelector(module); // withTargetModule
            }
            AppPlatformManager appPlatformManager = ASADeployUtils.getAppPlatformManager(management, subscriptionId);
            String DEFAULT_DEPLOYMENT_NAME = "default";
            DeploymentResourceInner resourceInner = appPlatformManager.serviceClient().getDeployments().get(resourceGroupName, serviceName, appName,
                    DEFAULT_DEPLOYMENT_NAME);
            resourceInner.properties().withSource(sourceInfo);
            appPlatformManager.serviceClient().getDeployments().update(resourceGroupName, serviceName, appName,
                    DEFAULT_DEPLOYMENT_NAME, resourceInner);
             log.info("Deploy source code to app {} completed.", appName);
        } catch (Exception e) {
            throw new RuntimeException("Deploy source code to app " + appName + " failed, error: " + e.getMessage());
        }
    }

    /**
     * Check build source status.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @return failed: Failed, null:  build source done
     */
    public String checkBuildSourceStatus(OAuth2AuthorizedClient management, String subscriptionId,
                                         String resourceGroupName,
                                         String serviceName, String appName) {
        AppPlatformManager appPlatformManager = ASADeployUtils.getAppPlatformManager(management, subscriptionId);
        Map<String, String> appStatusMap = ASADeployUtils.getAppAndInstanceStatus(appPlatformManager, resourceGroupName, serviceName, appName);
        String appStatus = appStatusMap.get("appStatus");
        String instanceStatus = appStatusMap.get("instanceStatus");
        //        build source failed
        if (STATUS_FAILED.equals(appStatus) && STATUS_RUNNING.equals(instanceStatus)) {
            return STATUS_FAILED;
        }
        return null;
    }

}
