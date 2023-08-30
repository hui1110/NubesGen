package io.github.nubesgen.service.azure.springapps;

import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.appplatform.fluent.models.BuildInner;
import com.azure.resourcemanager.appplatform.fluent.models.BuildResultInner;
import com.azure.resourcemanager.appplatform.fluent.models.BuildServiceAgentPoolResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.BuildServiceInner;
import com.azure.resourcemanager.appplatform.fluent.models.DeploymentResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.ServiceResourceInner;
import com.azure.resourcemanager.appplatform.models.BuildResultProvisioningState;
import com.azure.resourcemanager.appplatform.models.BuildResultUserSourceInfo;
import com.azure.resourcemanager.appplatform.models.BuildServiceAgentPoolProperties;
import com.azure.resourcemanager.appplatform.models.BuildServiceAgentPoolSizeProperties;
import com.azure.resourcemanager.appplatform.models.BuildServiceProperties;
import com.azure.resourcemanager.appplatform.models.BuildServicePropertiesResourceRequests;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceUtils;
import com.azure.resourcemanager.resources.fluentcore.utils.ResourceManagerUtils;
import io.github.nubesgen.model.azure.springapps.DeploymentParameter;
import io.github.nubesgen.utils.AzureResourceManagerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

import static io.github.nubesgen.service.azure.springapps.Constants.DEFAULT_DEPLOYMENT_NAME;

@Service
public class EnterpriseTierService implements ASAService {

    private final Logger log = LoggerFactory.getLogger(EnterpriseTierService.class);

    private final SpringAppsService springAppsService;

    public EnterpriseTierService(SpringAppsService springAppsService) {
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
            deploymentManager.getAppPlatformManager().serviceClient().getServices().createOrUpdate(resourceGroupName, serviceName, serviceResourceInner);
            log.info("Spring service {} created, tier: {}", serviceName, tier);

            BuildServiceProperties buildServiceProperties = new BuildServiceProperties();
            buildServiceProperties.withResourceRequests(new BuildServicePropertiesResourceRequests());
            deploymentManager.getAppPlatformManager().serviceClient().getBuildServices().createOrUpdate(resourceGroupName, serviceName, DEFAULT_DEPLOYMENT_NAME, new BuildServiceInner().withProperties(buildServiceProperties));
            log.info("Initialize build service for enterprise tier.");

            deploymentManager.getAppPlatformManager().serviceClient().getBuildServiceAgentPools()
                    .updatePut(
                            resourceGroupName,
                            serviceName,
                            DEFAULT_DEPLOYMENT_NAME,
                            DEFAULT_DEPLOYMENT_NAME,
                            new BuildServiceAgentPoolResourceInner()
                                    .withProperties(
                                            new BuildServiceAgentPoolProperties()
                                                    .withPoolSize(new BuildServiceAgentPoolSizeProperties().withName("S1")) // S1, S2, S3, S4, S5
                                    )
                    );
            log.info("Initialize build service agent pool for enterprise tier.");
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
            SpringService springService = deploymentManager.getAppPlatformManager().springServices().getByResourceGroup(resourceGroupName, serviceName);
            BuildInner buildInner = deploymentManager.getAppPlatformManager().serviceClient().getBuildServices().getBuild(resourceGroupName, serviceName, DEFAULT_DEPLOYMENT_NAME, appName);
            final String endpoint = springService.apps().getByName(appName).parent().listTestKeys().primaryTestEndpoint();
            final String logEndpoint = String.format("%s/api/logstream/buildpods/%s.%s-build-%s-build-pod/stages/%s?follow=true", endpoint.replace(".test", ""), DEFAULT_DEPLOYMENT_NAME, appName, ResourceUtils.nameFromResourceId(buildInner.properties().triggeredBuildResult().id()), stage);
            final String basicAuth = AzureResourceManagerUtils.getAuthorizationCode(springService, appName);
            buildLogs = springAppsService.getLogByUrl(logEndpoint, basicAuth);

            // get build status
            BuildResultInner buildResult = deploymentManager.getAppPlatformManager().serviceClient().getBuildServices()
                    .getBuildResult(
                            resourceGroupName,
                            serviceName,
                            DEFAULT_DEPLOYMENT_NAME,
                            appName,
                            ResourceUtils.nameFromResourceId(buildInner.properties().triggeredBuildResult().id()));
            BuildResultProvisioningState buildState = buildResult.properties().provisioningState();

            // wait for build log to be generated
            while ((buildState == BuildResultProvisioningState.QUEUING || buildState == BuildResultProvisioningState.BUILDING) && (buildLogs == null || buildLogs.isEmpty())) {
                buildLogs = springAppsService.getLogByUrl(logEndpoint, basicAuth);
                buildResult = deploymentManager.getAppPlatformManager().serviceClient().getBuildServices()
                        .getBuildResult(
                                resourceGroupName,
                                serviceName,
                                DEFAULT_DEPLOYMENT_NAME,
                                appName,
                                ResourceUtils.nameFromResourceId(buildInner.properties().triggeredBuildResult().id()));
                buildState = buildResult.properties().provisioningState();
                ResourceManagerUtils.sleep(Duration.ofSeconds(2));
            }
        } catch (Exception e) {
           log.error("Get build logs failed.", e);
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
                       String resourceGroupName, String serviceName, String appName, DeploymentParameter deploymentParameter) {
        log.info("Start deploy.....");
        try {
            BuildResultUserSourceInfo sourceInfo = new BuildResultUserSourceInfo();
            sourceInfo.withBuildResultId(deploymentParameter.getBuildId());
            DeploymentResourceInner resourceInner = deploymentManager.getAppPlatformManager().serviceClient().getDeployments().get(resourceGroupName, serviceName, appName,
                    DEFAULT_DEPLOYMENT_NAME);
            resourceInner.properties().withSource(sourceInfo);
            deploymentManager.getAppPlatformManager().serviceClient().getDeployments().update(resourceGroupName, serviceName, appName,
                    DEFAULT_DEPLOYMENT_NAME, resourceInner);
            log.info("Deploy source code to app {} in service {} completed.", appName, serviceName);
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
