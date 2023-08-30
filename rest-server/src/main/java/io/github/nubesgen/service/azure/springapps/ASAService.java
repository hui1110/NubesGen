package io.github.nubesgen.service.azure.springapps;

import com.azure.resourcemanager.appplatform.AppPlatformManager;
import io.github.nubesgen.model.azure.springapps.DeploymentParameter;

/**
 * Providing common operations for different tier, like provision, getBuildLogs, deploy
 */
public interface ASAService {

    void provision(DeploymentManager deploymentManager, String subscriptionId, String resourceGroupName, String serviceName, String region, String tier);

    String getBuildLogs(DeploymentManager deploymentManager, String subscriptionId,
                        String resourceGroupName,
                        String serviceName, String appName, String stage, String githubAction);

    void deploy(DeploymentManager deploymentManager, String subscriptionId,
                String resourceGroupName,
                String serviceName,
                String appName,
                DeploymentParameter parameter);
}
