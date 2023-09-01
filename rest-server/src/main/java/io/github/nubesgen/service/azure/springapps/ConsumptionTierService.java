package io.github.nubesgen.service.azure.springapps;

import com.azure.core.management.Region;
import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appcontainers.models.AppLogsConfiguration;
import com.azure.resourcemanager.appcontainers.models.LogAnalyticsConfiguration;
import com.azure.resourcemanager.appcontainers.models.ManagedEnvironment;
import com.azure.resourcemanager.appplatform.fluent.models.ServiceResourceInner;
import com.azure.resourcemanager.appplatform.models.ClusterResourceProperties;
import com.azure.resourcemanager.appplatform.models.NameAvailability;
import io.github.nubesgen.model.azure.springapps.DeploymentParameter;
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
                ServiceResourceInner serviceResourceInner = new ServiceResourceInner()
                        .withLocation(region)
                        .withSku(azureResourceManagerService.getSku(tier));

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
