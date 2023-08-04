package io.github.nubesgen.utils;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.appplatform.fluent.models.AppResourceInner;
import com.azure.resourcemanager.appplatform.models.AppResourceProperties;
import com.azure.resourcemanager.appplatform.models.DeploymentInstance;
import com.azure.resourcemanager.appplatform.models.SpringAppDeployment;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.resourcemanager.authorization.models.CertificateCredential;
import com.azure.resourcemanager.resources.models.Subscription;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import reactor.core.publisher.Mono;

import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ASADeployUtils {

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
     * @param subscriptionId subscriptionId
     * @return AppPlatformManager
     */
    public static AppPlatformManager getAppPlatformManager(OAuth2AuthorizedClient management, String subscriptionId) {
//        final TokenCredential credential = toTokenCredential(management.getAccessToken().getTokenValue());

        TokenCredential tokenCredential = new DefaultAzureCredentialBuilder().build();
        TokenRequestContext request = new TokenRequestContext().addScopes("https://management.azure.com/.default");
        AccessToken token =
                tokenCredential.getToken(request).retry(3L).blockOptional().orElseThrow(() -> new RuntimeException(
                        "Couldn't retrieve JWT"));
        final TokenCredential credential = toTokenCredential(token.getToken());

        final AzureProfile azureProfile = new AzureProfile(AzureEnvironment.AZURE);
        AzureResourceManager.Authenticated authenticated = AzureResourceManager.authenticate(credential, azureProfile);
        if (authenticated.subscriptions().list().stream().findAny().isEmpty()) {
            throw new RuntimeException("No subscription found !");
        }
        if (subscriptionId == null) {
            subscriptionId = authenticated.subscriptions().list().iterator().next().subscriptionId();
        }
        final String currentSubscriptionId = subscriptionId;
        Subscription subscription = authenticated.subscriptions().list().stream().filter(s -> s.subscriptionId().equals(currentSubscriptionId)).toList().get(0);
        final AzureProfile profile = new AzureProfile(subscription.innerModel().tenantId(), subscriptionId, AzureEnvironment.AZURE);
        return AppPlatformManager.authenticate(credential, profile);
    }

    public static Map<String, String> getCredential(OAuth2AuthorizedClient management, String subscriptionId) {
//        final TokenCredential credential = toTokenCredential(management.getAccessToken().getTokenValue());

        TokenCredential tokenCredential = new DefaultAzureCredentialBuilder().build();
        TokenRequestContext request = new TokenRequestContext().addScopes("https://management.azure.com/.default");
        AccessToken token =
                tokenCredential.getToken(request).retry(3L).blockOptional().orElseThrow(() -> new RuntimeException(
                        "Couldn't retrieve JWT"));
        final TokenCredential credential = toTokenCredential(token.getToken());

        final AzureProfile azureProfile = new AzureProfile(AzureEnvironment.AZURE);
        AzureResourceManager.Authenticated authenticated = AzureResourceManager.authenticate(credential, azureProfile);
        if (authenticated.subscriptions().list().stream().findAny().isEmpty()) {
            throw new RuntimeException("No subscription found !");
        }
        if (subscriptionId == null) {
            subscriptionId = authenticated.subscriptions().list().iterator().next().subscriptionId();
        }
        final String currentSubscriptionId = subscriptionId;
        Subscription subscription = authenticated.subscriptions().list().stream().filter(s -> s.subscriptionId().equals(currentSubscriptionId)).toList().get(0);
        Map<String, String> map = new HashMap<>();
        map.put("AZURE_SUBSCRIPTION_ID", subscriptionId);
        map.put("AZURE_TENANT_ID", subscription.innerModel().tenantId());
        map.put("AZURE_CLIENT_ID", management.getClientRegistration().getClientId());
        return map;
    }

    public static TokenCredential toTokenCredential(String accessToken) {
        return request -> Mono.just(new AccessToken(accessToken, OffsetDateTime.MAX));
    }

}
