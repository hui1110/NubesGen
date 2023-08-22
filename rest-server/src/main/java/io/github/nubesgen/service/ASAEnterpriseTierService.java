package io.github.nubesgen.service;

import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.appplatform.fluent.models.AppResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.BuildInner;
import com.azure.resourcemanager.appplatform.fluent.models.BuildResultInner;
import com.azure.resourcemanager.appplatform.fluent.models.BuildServiceAgentPoolResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.DeploymentResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.ServiceResourceInner;
import com.azure.resourcemanager.appplatform.models.BuildProperties;
import com.azure.resourcemanager.appplatform.models.BuildResultProvisioningState;
import com.azure.resourcemanager.appplatform.models.BuildResultUserSourceInfo;
import com.azure.resourcemanager.appplatform.models.BuildServiceAgentPoolProperties;
import com.azure.resourcemanager.appplatform.models.BuildServiceAgentPoolSizeProperties;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceUtils;
import com.azure.resourcemanager.resources.fluentcore.utils.ResourceManagerUtils;
import io.github.nubesgen.utils.ASADeployUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class ASAEnterpriseTierService implements ASAService {

    private final Logger log = LoggerFactory.getLogger(ASAEnterpriseTierService.class);
    private static final String DEFAULT_DEPLOYMENT_NAME = "default";

    public void provisionSpringApp(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName, String serviceName, String appName) {
        AppPlatformManager appPlatformManager = ASADeployUtils.getAppPlatformManager(management, subscriptionId);
        appPlatformManager.serviceClient().getBuildServiceAgentPools()
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
        AppResourceInner appResourceInner = ASADeployUtils.getAppResourceInner();
        appPlatformManager.serviceClient().getApps().createOrUpdate(resourceGroupName, serviceName, appName, appResourceInner);
        log.info("Provision spring app {} completed.", appName);
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
    @Override
    public String getBuildLogs(OAuth2AuthorizedClient management, String subscriptionId,
                               String resourceGroupName,
                               String serviceName, String appName, String stage, String githubAction) {
        String buildLogs = null;
        try {
            AppPlatformManager appPlatformManager = ASADeployUtils.getAppPlatformManager(management, subscriptionId);
            SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
            BuildInner buildInner = appPlatformManager.serviceClient().getBuildServices().getBuild(resourceGroupName, serviceName, DEFAULT_DEPLOYMENT_NAME, appName);
            final String endpoint = springService.apps().getByName(appName).parent().listTestKeys().primaryTestEndpoint();
            final String logEndpoint = String.format("%s/api/logstream/buildpods/%s.%s-build-%s-build-pod/stages/%s?follow=true", endpoint.replace(".test", ""), DEFAULT_DEPLOYMENT_NAME, appName, ResourceUtils.nameFromResourceId(buildInner.properties().triggeredBuildResult().id()), stage);
            final String basicAuth = ASADeployUtils.getAuthorizationCode(springService, appName);
            buildLogs = ASADeployUtils.getLogByUrl(logEndpoint, basicAuth);

            // get build status
            BuildResultInner buildResult = appPlatformManager.serviceClient().getBuildServices()
                    .getBuildResult(
                            resourceGroupName,
                            serviceName,
                            DEFAULT_DEPLOYMENT_NAME,
                            appName,
                            ResourceUtils.nameFromResourceId(buildInner.properties().triggeredBuildResult().id()));
            BuildResultProvisioningState buildState = buildResult.properties().provisioningState();

            // wait for build log to be generated
            while ((buildState == BuildResultProvisioningState.QUEUING || buildState == BuildResultProvisioningState.BUILDING) && (buildLogs == null || buildLogs.isEmpty())) {
                buildLogs = ASADeployUtils.getLogByUrl(logEndpoint, basicAuth);
                buildResult = appPlatformManager.serviceClient().getBuildServices()
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
     * Enqueue build to Azure Spring App instance(Enterprise).
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param relativePath the relative path
     * @param region the region
     * @param javaVersion the java version
     * @param module the module
     * @return Succeeded, build result id or failed
     */
    public String enqueueBuild(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName, String serviceName, String appName, String relativePath, String region, String javaVersion, String module) {
        AppPlatformManager appPlatformManager = ASADeployUtils.getAppPlatformManager(management, subscriptionId);
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);

        ServiceResourceInner serviceResourceInner = new ServiceResourceInner()
                .withLocation(region)
                .withSku(springService.sku());
        serviceResourceInner = appPlatformManager.serviceClient().getServices().createOrUpdate(resourceGroupName, serviceName, serviceResourceInner);

        BuildProperties buildProperties = new BuildProperties()
                .withBuilder(String.format("%s/buildservices/%s/builders/%s", serviceResourceInner.id(), "default", "default"))
                .withAgentPool(String.format("%s/buildservices/%s/agentPools/%s", serviceResourceInner.id(), "default", "default"))
                .withRelativePath(relativePath);

        Map<String, String> buildEnv = new HashMap<>();
        // java version
        buildEnv.put("BP_JVM_VERSION", javaVersion);
        if (!Objects.equals(module, "null")) {
            // target module
            buildEnv.put("BP_MAVEN_BUILT_MODULE", module);
        }
        buildProperties.withEnv(buildEnv);

        // enqueue build
        BuildInner enqueueResult = appPlatformManager.serviceClient().getBuildServices()
                .createOrUpdateBuild(
                        resourceGroupName,
                        serviceName,
                        DEFAULT_DEPLOYMENT_NAME,
                        appName,
                        new BuildInner().withProperties(buildProperties)
                );
        log.info("--------Enqueue build.------------");
        return enqueueResult.properties().triggeredBuildResult().id();
    }

    /**
     * Build source code to Azure Spring App instance(Enterprise).
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param buildId the build id
     * @return Succeeded or failed
     */
    public String buildSourceCode(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName, String serviceName, String appName, String buildId) {
        AppPlatformManager appPlatformManager = ASADeployUtils.getAppPlatformManager(management, subscriptionId);

        // get build state
        BuildResultInner buildResult = appPlatformManager.serviceClient().getBuildServices()
                .getBuildResult(
                        resourceGroupName,
                        serviceName,
                        DEFAULT_DEPLOYMENT_NAME,
                        appName,
                        ResourceUtils.nameFromResourceId(buildId));
        BuildResultProvisioningState buildState = buildResult.properties().provisioningState();
        log.info("--------Get build state.------------");

        // wait for build
        while (buildState == BuildResultProvisioningState.QUEUING || buildState == BuildResultProvisioningState.BUILDING) {
            buildResult = appPlatformManager.serviceClient().getBuildServices()
                    .getBuildResult(
                            resourceGroupName,
                            serviceName,
                            DEFAULT_DEPLOYMENT_NAME,
                            appName,
                            ResourceUtils.nameFromResourceId(buildId));
            buildState = buildResult.properties().provisioningState();
            ResourceManagerUtils.sleep(Duration.ofSeconds(30));
        }
        log.info("--------Build completed.------------");
        return buildState.toString();
    }


    /**
     * Deploy to Azure Spring App instance(Enterprise).
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param buildId the build id
     */
    public void deploy(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName, String serviceName, String appName, String buildId) {
        log.info("Start deploy.....");
        try {
            AppPlatformManager appPlatformManager = ASADeployUtils.getAppPlatformManager(management, subscriptionId);

            BuildResultUserSourceInfo sourceInfo = new BuildResultUserSourceInfo();
            sourceInfo.withBuildResultId(buildId);

            DeploymentResourceInner resourceInner = appPlatformManager.serviceClient().getDeployments().get(resourceGroupName, serviceName, appName,
                    DEFAULT_DEPLOYMENT_NAME);
            resourceInner.properties().withSource(sourceInfo);
            appPlatformManager.serviceClient().getDeployments().update(resourceGroupName, serviceName, appName,
                    DEFAULT_DEPLOYMENT_NAME, resourceInner);
            log.info("Deploy source code to app {} in service {} completed.", appName, serviceName);
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
