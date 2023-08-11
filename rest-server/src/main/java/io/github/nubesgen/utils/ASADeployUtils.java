package io.github.nubesgen.utils;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.appplatform.fluent.models.AppResourceInner;
import com.azure.resourcemanager.appplatform.models.AppResourceProperties;
import com.azure.resourcemanager.appplatform.models.DeploymentInstance;
import com.azure.resourcemanager.appplatform.models.SpringAppDeployment;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.resourcemanager.resources.models.Subscription;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.eclipse.jgit.api.Git;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import reactor.core.publisher.Mono;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ASADeployUtils {

    /**
     * Download the source code from the GitHub repository.
     *
     * @param url The git repository url.
     * @param branchName The branch name.
     * @return The path of the source code.
     */
    public static synchronized String downloadSourceCodeFromGitHub(String url, String branchName) {
        String repositoryPath = url.substring(url.lastIndexOf("/") + 1);
        deleteRepositoryDirectory(new File(repositoryPath));
        branchName = Objects.equals(branchName, "null") ? null : branchName;
        Git git = null;
        String pathName = null;
        try {
            git = Git.cloneRepository()
                    .setURI(url)
                    .setBranch(branchName)
                    .call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (git != null) {
                git.close();
                pathName = git.getRepository().getWorkTree().toString();
            }
        }
        return pathName;
    }

    /**
     * Delete the directory.
     *
     * @param directory The directory.
     */
    public static synchronized void deleteRepositoryDirectory(File directory) {
        File tempGitDirectory;
        try {
            tempGitDirectory = new File(directory.toString());
            if (tempGitDirectory.exists()) {
                FileUtils.deleteDirectory(tempGitDirectory);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get app resource inner.
     *
     * @return app resource inner
     */
    public static AppResourceInner getAppResourceInner() {
        AppResourceProperties properties = new AppResourceProperties();
        properties.withHttpsOnly(true);
        properties.withPublicProperty(true);

        AppResourceInner appResourceInner = new AppResourceInner();
        appResourceInner.withProperties(properties);
        return appResourceInner;
    }

    /**
     * Get log by url string.
     *
     * @param strUrl url
     * @param basicAuth basicAuth
     * @return log string
     */
    public static String getLogByUrl(String strUrl, String basicAuth) {
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(strUrl);
            connection = (HttpsURLConnection) url.openConnection();
            if (basicAuth != null) {
                connection.setRequestProperty("Authorization", basicAuth);
            }
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            byte[] getData = connection.getInputStream().readAllBytes();
            return new String(getData);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get authorization code.
     *
     * @param springService spring service
     * @param appName app name
     * @return authorization code
     */
    public static String getAuthorizationCode(SpringService springService, String appName) {
        final String password = springService.apps().getByName(appName).parent().listTestKeys().primaryKey();
        final String userPass = "primary:" + password;
        return "Basic " + new String(Base64.getEncoder().encode(userPass.getBytes()));
    }

    /**
     * Get app status and instance status.
     *
     * @param appPlatformManager appPlatformManager
     * @param resourceGroupName resourceGroupName
     * @param serviceName serviceName
     * @param appName appName
     * @return app status and instance status
     */
    public static Map<String, String> getAppAndInstanceStatus(AppPlatformManager appPlatformManager, String resourceGroupName,
                                                              String serviceName, String appName) {
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        SpringAppDeployment springAppDeployment = springService.apps().getByName(appName).getActiveDeployment();
        String appStatus = springAppDeployment.innerModel().properties().provisioningState().toString();

        List<DeploymentInstance> deploymentInstances = springAppDeployment.instances();
        deploymentInstances = deploymentInstances.stream().sorted(Comparator.comparing(DeploymentInstance::startTime)).collect(Collectors.toList());
        String instanceStatus = deploymentInstances.get(deploymentInstances.size() - 1).status();

        Map<String, String> appAndInstanceStatus = new HashMap<>();
        appAndInstanceStatus.put("appStatus", appStatus);
        appAndInstanceStatus.put("instanceStatus", instanceStatus);
        return appAndInstanceStatus;
    }

    /**
     * Get AppPlatformManager
     *
     * @param management OAuth2AuthorizedClient
     * @param subscriptionId subscription id
     * @return AppPlatformManager
     */
    public static AppPlatformManager getAppPlatformManager(OAuth2AuthorizedClient management, String subscriptionId) {
        AzureResourceManager.Authenticated authenticated = getARMAuthenticated(management);
        Subscription subscription = getSubscription(authenticated, subscriptionId);
        final AzureProfile profile = new AzureProfile(subscription.innerModel().tenantId(), subscriptionId, AzureEnvironment.AZURE);
        final TokenCredential credential = getAzureCredential(management);
        return AppPlatformManager.authenticate(credential, profile);
    }

    /**
     * Get Service Principal operation AzureResourceManager.
     *
     * @param management OAuth2AuthorizedClient
     * @param subscriptionId subscription id
     * @return AzureResourceManager
     */
    public static AzureResourceManager getServicePrincipalOperationARM(OAuth2AuthorizedClient management, String subscriptionId) {
        final AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
        Subscription subscription = azureResourceManager.subscriptions().list().stream().filter(s -> s.subscriptionId().equals(subscriptionId)).toList().get(0);
        final AzureProfile servicePrincipalProfile = new AzureProfile(subscription.innerModel().tenantId(), subscriptionId, AzureEnvironment.AZURE);
        final TokenCredential servicePrincipalCredential = new ClientSecretCredentialBuilder()
                .clientId(management.getClientRegistration().getClientId())
                .clientSecret(management.getClientRegistration().getClientSecret())
                .authorityHost(servicePrincipalProfile.getEnvironment().getActiveDirectoryEndpoint())
                .tenantId(subscription.innerModel().tenantId())
                .build();
        return AzureResourceManager
                .authenticate(servicePrincipalCredential, servicePrincipalProfile)
                .withSubscription(subscriptionId);
    }

    /**
     * Get AzureResourceManager.
     *
     * @param management OAuth2AuthorizedClient
     * @param subscriptionId subscription id
     * @return AzureResourceManager
     */
    public static AzureResourceManager getAzureResourceManager(OAuth2AuthorizedClient management, String subscriptionId) {
        return getARMAuthenticated(management).withSubscription(subscriptionId);
    }

    /**
     * Get tenant id.
     *
     * @param management OAuth2AuthorizedClient
     * @param subscriptionId subscription id
     * @return tenant id
     */
    public static String getTenantId(OAuth2AuthorizedClient management, String subscriptionId) {
        AzureResourceManager.Authenticated authenticated = getARMAuthenticated(management);
        Subscription subscription = getSubscription(authenticated, subscriptionId);
        return subscription.innerModel().tenantId();
    }

    private static AzureResourceManager.Authenticated getARMAuthenticated(OAuth2AuthorizedClient management) {
        final TokenCredential credential = getAzureCredential(management);
        final AzureProfile azureProfile = new AzureProfile(AzureEnvironment.AZURE);
        AzureResourceManager.Authenticated authenticated = AzureResourceManager.authenticate(credential, azureProfile);
        if (authenticated.subscriptions().list().stream().findAny().isEmpty()) {
            throw new RuntimeException("No subscription found !");
        }
        return authenticated;
    }

    private static Subscription getSubscription(AzureResourceManager.Authenticated authenticated, String subscriptionId) {
        if (subscriptionId == null) {
            subscriptionId = authenticated.subscriptions().list().iterator().next().subscriptionId();
        }
        final String currentSubscriptionId = subscriptionId;
        return authenticated.subscriptions().list().stream().filter(s -> s.subscriptionId().equals(currentSubscriptionId)).toList().get(0);
    }

    private static TokenCredential getAzureCredential(OAuth2AuthorizedClient management) {
        return toTokenCredential(management.getAccessToken().getTokenValue());
    }

    private static TokenCredential toTokenCredential(String accessToken) {
        return request -> Mono.just(new AccessToken(accessToken, OffsetDateTime.MAX));
    }

}
