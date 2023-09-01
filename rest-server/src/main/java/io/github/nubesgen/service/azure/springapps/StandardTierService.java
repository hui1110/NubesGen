package io.github.nubesgen.service.azure.springapps;

import com.azure.core.management.Region;
import com.azure.resourcemanager.appplatform.fluent.models.DeploymentResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.ServiceResourceInner;
import com.azure.resourcemanager.appplatform.models.NameAvailability;
import com.azure.resourcemanager.appplatform.models.SourceUploadedUserSourceInfo;
import com.azure.resourcemanager.appplatform.models.SpringAppDeployment;
import com.azure.resourcemanager.appplatform.models.SpringService;
import io.github.nubesgen.model.azure.springapps.DeploymentParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static io.github.nubesgen.service.azure.springapps.Constants.DEFAULT_DEPLOYMENT_NAME;

/**
 * Providing operations for Standard tier, like provisionSpringService, getBuildLogs, deploy.
 */
@Service
public class StandardTierService extends AbstractSpringAppsService {

    private final Logger log = LoggerFactory.getLogger(StandardTierService.class);
    private final AzureResourceManagerService azureResourceManagerService;
    private final SpringAppsLogService logService;

    public StandardTierService(AzureResourceManagerService azureResourceManagerService, SpringAppsLogService logService) {
        super(logService, azureResourceManagerService);
        this.azureResourceManagerService = azureResourceManagerService;
        this.logService = logService;
    }

    /**
     * Provision spring service.
     *
     * @param deploymentManager ARM client
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param region the region name
     * @param tier the tier name
     */
    @Override
    public void provisionSpringService(DeploymentManager deploymentManager, String subscriptionId, String resourceGroupName,
                                       String serviceName, String region, String tier) {
        try {
            NameAvailability nameAvailability = deploymentManager.getAppPlatformManager()
                    .springServices().checkNameAvailability(serviceName, Region.fromName(region));
            if (nameAvailability.nameAvailable()) {
                ServiceResourceInner serviceResourceInner = new ServiceResourceInner()
                        .withLocation(region)
                        .withSku(azureResourceManagerService.getSku(tier));
                deploymentManager.getAppPlatformManager().serviceClient()
                        .getServices().createOrUpdate(resourceGroupName, serviceName, serviceResourceInner);
                log.info("Spring service {} created, tier: {}", serviceName, tier);
            } else {
                log.info("Spring service {} already exists.", serviceName);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
    @Override
    public String getBuildLogs(DeploymentManager deploymentManager, String subscriptionId,
                               String resourceGroupName,
                               String serviceName, String appName, String stage, String githubAction) {
        String buildLogs = null;
        try {
            SpringService springService = deploymentManager.getAppPlatformManager()
                    .springServices().getByResourceGroup(resourceGroupName, serviceName);
            SpringAppDeployment springAppDeployment = springService.apps().getByName(appName).getActiveDeployment();
            if (Objects.isNull(springAppDeployment) || Objects.isNull(springAppDeployment.getLogFileUrl())) {
                throw new IllegalStateException("Spring app deployment or deployment log file not exist - " + springAppDeployment);
            }
            buildLogs = logService.getLogByUrl(springAppDeployment.getLogFileUrl(), null);
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
    @Override
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
            DeploymentResourceInner resourceInner = deploymentManager.getAppPlatformManager()
                    .serviceClient().getDeployments().get(resourceGroupName, serviceName, appName, DEFAULT_DEPLOYMENT_NAME);
            resourceInner.properties().withSource(sourceInfo);
            deploymentManager.getAppPlatformManager().serviceClient()
                    .getDeployments().update(resourceGroupName, serviceName, appName, DEFAULT_DEPLOYMENT_NAME, resourceInner);
            log.info("Deploy source code to app {} completed.", appName);
        } catch (Exception e) {
            throw new RuntimeException("Deploy source code to app " + appName + " failed, error: " + e.getMessage());
        }
    }

}
