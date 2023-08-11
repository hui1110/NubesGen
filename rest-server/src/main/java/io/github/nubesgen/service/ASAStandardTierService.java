package io.github.nubesgen.service;

import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.appplatform.fluent.models.AppResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.DeploymentResourceInner;
import com.azure.resourcemanager.appplatform.models.SourceUploadedUserSourceInfo;
import com.azure.resourcemanager.appplatform.models.SpringAppDeployment;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.resourcemanager.resources.fluentcore.utils.ResourceManagerUtils;
import io.github.nubesgen.model.GitHubRepositoryPublicKey;
import io.github.nubesgen.utils.ASADeployUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import static com.azure.core.util.polling.implementation.PollingConstants.STATUS_FAILED;
import static com.azure.core.util.polling.implementation.PollingConstants.STATUS_RUNNING;

@Service
public class ASAStandardTierService implements ASAService {

    private final Logger log = LoggerFactory.getLogger(ASAStandardTierService.class);
    private final ASAGitHubActionService asaGitHubActionService;

    public ASAStandardTierService(ASAGitHubActionService asaGitHubActionService) {
        this.asaGitHubActionService = asaGitHubActionService;
    }

    /**
     * Provision resource.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     */
    public void provisionSpringApp(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName,
                                  String serviceName,
                                  String appName) {
        AppPlatformManager appPlatformManager = ASADeployUtils.getAppPlatformManager(management, subscriptionId);
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
     * @param githubAction the GitHub action
     */
    public void buildAndDeploySourceCode(OAuth2AuthorizedClient management, String subscriptionId,
                               String resourceGroupName,
                               String serviceName,
                               String appName,
                               String module, String javaVersion,
                               String relativePath,
                               String githubAction,
                               String authorizationCode,
                               String url,
                               String branchName){
        log.info("Start build and deploy source code in Standard tier service {}.....", serviceName);
        try {
            if(githubAction.equals("true")) {
                branchName = Objects.equals(branchName, "null") ? "main" : branchName;

                String accessToken = asaGitHubActionService.getAccessToken(authorizationCode);
                log.info("Get access token: {}", accessToken);

                String username = asaGitHubActionService.getUserName(url);
                log.info("Get username {}.", username);

                String pathName = ASADeployUtils.downloadSourceCodeFromGitHub(url, branchName);
                log.info("Cloned repository to {}.", pathName);

                String tenantId = ASADeployUtils.getTenantId(management, subscriptionId);
                Map<String, String> servicePrincipalIdMap = asaGitHubActionService.createdServicePrincipal(management, tenantId, subscriptionId, appName);

                if (servicePrincipalIdMap.isEmpty()) {
                    log.info("Service principal exist.");
                }else {
                    log.info("Service principal not exist. start to create.");
                    GitHubRepositoryPublicKey repositoryPublicKey = asaGitHubActionService.getGitHubRepositoryPublicKey(username, pathName, accessToken);
                    log.info("Get public key {}.", repositoryPublicKey.getKeyId());

                    asaGitHubActionService.pushSecretsToGitHub(management, subscriptionId, serviceName, appName, username, pathName, accessToken, repositoryPublicKey, servicePrincipalIdMap.get("clientId"));
                    log.info("Push secrets to GitHub repository completed.");

                    asaGitHubActionService.assignedRoleToServicePrincipal(management, subscriptionId, servicePrincipalIdMap.get("principalId"));
                    log.info("Add role assignment for subscription completed.");

                    asaGitHubActionService.createFederatedCredential(management, appName, tenantId, servicePrincipalIdMap.get("objectId"), username, pathName, branchName);
                    log.info("Created federated identity credential.");
                }
                boolean workflowFileChange = asaGitHubActionService.createWorkflowFile(pathName, branchName, javaVersion, module);
                log.info("Created workflow file.");

                if(workflowFileChange) {
                    asaGitHubActionService.pushWorkflowFile(pathName, url, branchName, username, accessToken);
                    log.info("Push workflow file to GitHub complete.");
                }else {
                    asaGitHubActionService.startGitHubAction(username, pathName, accessToken, branchName);
                    log.info("No change in workflow file, start GitHub action again.");
                }

//              Waiting for workflow to start
                ResourceManagerUtils.sleep(Duration.ofSeconds(10));
                String status = asaGitHubActionService.getGitHubActionStatus(username, pathName, accessToken);
                while (!status.equals("completed")) {
                    ResourceManagerUtils.sleep(Duration.ofSeconds(10));
                    status = asaGitHubActionService.getGitHubActionStatus(username, pathName, accessToken);
                }
                log.info("GitHub action run complete.");
            }else {
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
            }
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
