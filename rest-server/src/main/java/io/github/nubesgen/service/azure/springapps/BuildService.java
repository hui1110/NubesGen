package io.github.nubesgen.service.azure.springapps;

import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.appplatform.fluent.models.BuildInner;
import com.azure.resourcemanager.appplatform.fluent.models.BuildResultInner;
import com.azure.resourcemanager.appplatform.fluent.models.ServiceResourceInner;
import com.azure.resourcemanager.appplatform.models.BuildProperties;
import com.azure.resourcemanager.appplatform.models.BuildResultProvisioningState;
import com.azure.resourcemanager.appplatform.models.BuilderProvisioningState;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceUtils;
import com.azure.resourcemanager.resources.fluentcore.utils.ResourceManagerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.azure.core.util.polling.implementation.PollingConstants.STATUS_FAILED;
import static com.azure.core.util.polling.implementation.PollingConstants.STATUS_RUNNING;
import static com.azure.core.util.polling.implementation.PollingConstants.STATUS_SUCCEEDED;
import static io.github.nubesgen.service.azure.springapps.Constants.DEFAULT_DEPLOYMENT_NAME;

/**
 * Providing operations for build source code.
 */
@Service
public class BuildService {

    private final Logger log = LoggerFactory.getLogger(BuildService.class);
    private final StandardTierService standardTierService;

    public BuildService(StandardTierService standardTierService) {
        this.standardTierService = standardTierService;
    }

    /**
     * Check build source status.
     *
     * @param deploymentManager ARM client.
     * @param resourceGroupName the resource group name.
     * @param serviceName the service name.
     * @param appName the app name.
     * @return failed: Failed, null:  build source done.
     */
    public String checkBuildSourceStatus(DeploymentManager deploymentManager,
                                         String resourceGroupName,
                                         String serviceName, String appName) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();
        Map<String, String> appStatusMap = standardTierService.getAppAndInstanceStatus(appPlatformManager, resourceGroupName, serviceName, appName);
        String appStatus = appStatusMap.get("appStatus");
        String instanceStatus = appStatusMap.get("instanceStatus");
        //        build source failed
        if (STATUS_FAILED.equals(appStatus) && STATUS_RUNNING.equals(instanceStatus)) {
            return STATUS_FAILED;
        }
        return null;
    }

    /**
     * Enqueue build to Azure Spring App instance(Enterprise).
     *
     * @param deploymentManager ARM client.
     * @param resourceGroupName the resource group name.
     * @param serviceName the service name.
     * @param appName the app name.
     * @param relativePath the relative path.
     * @param region the region.
     * @param javaVersion the java version.
     * @param module the module name.
     * @return Succeeded, build result id or failed.
     */
    public String enqueueBuild(DeploymentManager deploymentManager, String resourceGroupName,
            String serviceName, String appName, String relativePath, String region,
            String javaVersion, String module
    ) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();

        BuilderProvisioningState buildServiceProvisioningState = appPlatformManager.serviceClient().getBuildServiceBuilders().get(
                resourceGroupName, serviceName, DEFAULT_DEPLOYMENT_NAME, DEFAULT_DEPLOYMENT_NAME
        ).properties().provisioningState();
        String provisioningState = buildServiceProvisioningState.toString();
        // wait for builders ready
        while (!provisioningState.equals(STATUS_SUCCEEDED)) {
            log.info("Builders is not ready, status: {}, wait for 10 seconds.", provisioningState);
            buildServiceProvisioningState = appPlatformManager.serviceClient().getBuildServiceBuilders().get(
                    resourceGroupName, serviceName, DEFAULT_DEPLOYMENT_NAME, DEFAULT_DEPLOYMENT_NAME
            ).properties().provisioningState();
            provisioningState = buildServiceProvisioningState.toString();
            if(provisioningState.equals(STATUS_FAILED)){
                throw new RuntimeException("Create Builders failed.");
            }
            ResourceManagerUtils.sleep(Duration.ofSeconds(10));
        }
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
        log.info("Enqueue build completed.");
        return enqueueResult.properties().triggeredBuildResult().id();
    }

    /**
     * Build source code to Azure Spring App instance(Enterprise).
     *
     * @param deploymentManager ARM client.
     * @param resourceGroupName the resource group name.
     * @param serviceName the service name.
     * @param appName the app name.
     * @param buildId the build id.
     * @return Succeeded or failed.
     */
    public String buildSourceCode(DeploymentManager deploymentManager, String resourceGroupName, String serviceName, String appName, String buildId) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();

        // get build state
        BuildResultInner buildResult = appPlatformManager.serviceClient().getBuildServices()
                .getBuildResult(
                        resourceGroupName,
                        serviceName,
                        DEFAULT_DEPLOYMENT_NAME,
                        appName,
                        ResourceUtils.nameFromResourceId(buildId));
        BuildResultProvisioningState buildState = buildResult.properties().provisioningState();
        log.info("Get build state.");

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
        log.info("Build completed.");
        return buildState.toString();
    }

}
