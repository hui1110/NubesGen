package io.github.nubesgen.service.azure.springapps;

import com.azure.core.http.rest.Response;
import com.azure.core.management.Region;
import com.azure.core.util.Context;
import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appcontainers.models.AppLogsConfiguration;
import com.azure.resourcemanager.appcontainers.models.LogAnalyticsConfiguration;
import com.azure.resourcemanager.appcontainers.models.ManagedEnvironment;
import com.azure.resourcemanager.appplatform.fluent.models.ServiceResourceInner;
import com.azure.resourcemanager.appplatform.models.ClusterResourceProperties;
import com.azure.resourcemanager.appplatform.models.NameAvailability;
import com.azure.resourcemanager.loganalytics.LogAnalyticsManager;
import com.azure.resourcemanager.loganalytics.models.SharedKeys;
import com.azure.resourcemanager.loganalytics.models.Workspace;
import com.azure.resourcemanager.loganalytics.models.WorkspaceSku;
import com.azure.resourcemanager.loganalytics.models.WorkspaceSkuNameEnum;
import io.github.nubesgen.model.azure.springapps.DeploymentParameter;
import io.github.nubesgen.utils.AzureResourceManagerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;


/**
 * Providing operations for Consumption tier, like provisionSpringService, getBuildLogs, deploy.
 */
@Service
public class ConsumptionTierService extends AbstractSpringAppsService {

    private final Logger log = LoggerFactory.getLogger(ConsumptionTierService.class);
    private final AzureResourceManagerService azureResourceManagerService;

    public ConsumptionTierService(SpringAppsLogService springAppsLogService, AzureResourceManagerService azureResourceManagerService) {
        super(springAppsLogService, azureResourceManagerService);
        this.azureResourceManagerService = azureResourceManagerService;
    }

    @Override
    public void provisionSpringService(DeploymentManager deploymentManager, String subscriptionId,
                                       String resourceGroupName, String serviceName, String region, String tier) {
        try {
            NameAvailability nameAvailability = deploymentManager.getAppPlatformManager().springServices().checkNameAvailability(serviceName, Region.fromName(region));
            if (nameAvailability.nameAvailable()) {
                // create log analytics workspace
                LogAnalyticsManager logAnalyticsManager = deploymentManager.getLogAnalyticsManager();
                Workspace workspace = logAnalyticsManager
                        .workspaces()
                        .define(serviceName)
                        .withRegion(region)
                        .withExistingResourceGroup(resourceGroupName)
                        .withSku(new WorkspaceSku().withName(WorkspaceSkuNameEnum.PER_GB2018))
                        .withRetentionInDays(30)
                        .create();
                log.info("Create log analytics workspace {} completed", workspace.name());

                // get log analytics workspace shared key
                Response<SharedKeys> sharedKeysResponse = logAnalyticsManager
                        .sharedKeysOperations()
                                .getSharedKeysWithResponse(workspace.resourceGroupName(), workspace.name(), Context.NONE);;
                log.info("Get log analytics workspace shared key completed");

                // create container environment
                ContainerAppsApiManager containerAppsApiManager = deploymentManager.getContainerAppsApiManager();
                ManagedEnvironment managedEnvironment = containerAppsApiManager.managedEnvironments().define("cae-" + serviceName)
                        .withRegion(region)
                        .withExistingResourceGroup(resourceGroupName)
                        .withAppLogsConfiguration(
                                new AppLogsConfiguration()
                                        .withDestination("log-analytics")
                                        .withLogAnalyticsConfiguration(
                                                new LogAnalyticsConfiguration().withCustomerId(workspace.customerId())
                                                        .withSharedKey(sharedKeysResponse.getValue().secondarySharedKey())))
                        .create();
                log.info("Create container environment {} completed", managedEnvironment.name());

                ServiceResourceInner serviceResourceInner = new ServiceResourceInner()
                        .withLocation(region)
                        .withSku(azureResourceManagerService.getSku(tier));
                ClusterResourceProperties clusterResourceProperties = new ClusterResourceProperties();
                clusterResourceProperties.withManagedEnvironmentId(managedEnvironment.id());
                serviceResourceInner.withProperties(clusterResourceProperties);
                deploymentManager.getAppPlatformManager().serviceClient().getServices().createOrUpdate(resourceGroupName, serviceName, serviceResourceInner);
                log.info("Spring service {} created, tier: {}", serviceName, tier);
            } else {
                log.info("Spring service {} already exists.", serviceName);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getBuildLogs(DeploymentManager deploymentManager, String subscriptionId, String resourceGroupName,
                               String serviceName, String appName, String stage, String githubAction) {
        return null;
    }

    @Override
    public void deploy(DeploymentManager deploymentManager, String subscriptionId, String resourceGroupName,
                       String serviceName, String appName, DeploymentParameter parameter) {

    }

}
