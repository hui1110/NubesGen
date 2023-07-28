package io.github.nubesgen.service;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

public interface ASAService {

    void provisionResource(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName,
                           String serviceName,
                           String appName, String region, String skuName);

    String getBuildLogs(OAuth2AuthorizedClient management, String subscriptionId,
                        String resourceGroupName,
                        String serviceName, String appName, String stage);
}
