package io.github.nubesgen.service;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

public interface ASAService {

    void provisionSpringService(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName, String serviceName, String region, String tier);

    String getBuildLogs(OAuth2AuthorizedClient management, String subscriptionId,
                        String resourceGroupName,
                        String serviceName, String appName, String stage, String githubAction);
}
