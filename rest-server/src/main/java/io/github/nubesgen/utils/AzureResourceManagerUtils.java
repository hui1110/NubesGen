package io.github.nubesgen.utils;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.resourcemanager.resources.models.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Base64;

public final class AzureResourceManagerUtils {

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
     * Get AppPlatformManager
     *
     * @param management OAuth2AuthorizedClient
     * @param subscriptionId subscription id
     * @return AppPlatformManager
     */
    public static AppPlatformManager getAppPlatformManager(OAuth2AuthorizedClient management, String subscriptionId) {
        AzureResourceManager.Authenticated authenticated = getARMAuthenticated(management);
        Subscription subscription = getSubscription(authenticated, subscriptionId);
        final AzureProfile profile = new AzureProfile(subscription.innerModel().tenantId(), subscription.subscriptionId(), AzureEnvironment.AZURE);
        final TokenCredential credential = getAzureCredential(management);
        return AppPlatformManager.authenticate(credential, profile);
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
     * Get ContainerAppsApiManager.
     *
     * @param management OAuth2AuthorizedClient
     * @param subscriptionId subscription id
     * @return ContainerAppsApiManager
     */
    public static ContainerAppsApiManager getContainerAppsApiManager(OAuth2AuthorizedClient management, String subscriptionId) {
        AzureResourceManager.Authenticated authenticated = getARMAuthenticated(management);
        Subscription subscription = getSubscription(authenticated, subscriptionId);
        final AzureProfile profile = new AzureProfile(subscription.innerModel().tenantId(), subscriptionId, AzureEnvironment.AZURE);
        final TokenCredential credential = getAzureCredential(management);
        return ContainerAppsApiManager.authenticate(credential, profile);
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

    private static Subscription getSubscription(AzureResourceManager.Authenticated authenticated, String subscriptionId) {
        if (subscriptionId == null) {
            subscriptionId = authenticated.subscriptions().list().iterator().next().subscriptionId();
        }
        final String currentSubscriptionId = subscriptionId;
        return authenticated.subscriptions().list().stream().filter(s -> s.subscriptionId().equals(currentSubscriptionId)).toList().get(0);
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

    private static TokenCredential getAzureCredential(OAuth2AuthorizedClient management) {
        TokenCredential tokenCredential = new DefaultAzureCredentialBuilder().build();
        TokenRequestContext request = new TokenRequestContext().addScopes("https://management.azure.com/.default");
        AccessToken token =
                tokenCredential.getToken(request).retry(3L).blockOptional().orElseThrow(() -> new RuntimeException(
                        "Couldn't retrieve JWT"));
//        toTokenCredential(management.getAccessToken().getTokenValue());
        return toTokenCredential(token.getToken());
    }

    public static TokenCredential toTokenCredential(String accessToken) {
        return request -> Mono.just(new AccessToken(accessToken, OffsetDateTime.MAX));
    }

}
