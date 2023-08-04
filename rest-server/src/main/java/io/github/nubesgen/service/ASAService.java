package io.github.nubesgen.service;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

public interface ASAService {

    void provisionSpringApp(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName,
                           String serviceName,
                           String appName);

    String getBuildLogs(OAuth2AuthorizedClient management, String subscriptionId,
                        String resourceGroupName,
                        String serviceName, String appName, String stage);
}
