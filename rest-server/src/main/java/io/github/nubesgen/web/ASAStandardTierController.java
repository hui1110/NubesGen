package io.github.nubesgen.web;

import io.github.nubesgen.model.azure.springapps.DeploymentParameter;
import io.github.nubesgen.service.azure.springapps.DeploymentManager;
import io.github.nubesgen.service.azure.springapps.StandardTierService;
import io.github.nubesgen.service.azure.springapps.BuildService;
import io.github.nubesgen.utils.AzureResourceManagerUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import static com.azure.core.util.polling.implementation.PollingConstants.STATUS_FAILED;
import static io.github.nubesgen.service.azure.springapps.Constants.DEFAULT_OAUTH2_CLIENT;

@RestController
@RequestMapping("/springapps/standard")
public class ASAStandardTierController {

    private final StandardTierService asaStandardTierService;
    private final BuildService buildService;

    public ASAStandardTierController(StandardTierService asaStandardTierService, BuildService buildService) {
        this.asaStandardTierService = asaStandardTierService;
        this.buildService = buildService;
    }

    @GetMapping("/provision")
    public @ResponseBody ResponseEntity<?> provisionSpringApp(
        @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
        @RequestParam String subscriptionId,
        @RequestParam String resourceGroupName,
        @RequestParam String serviceName,
        @RequestParam String region,
        @RequestParam String tier
    ) {
        try {
            asaStandardTierService.provision(
                new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId),
                    AzureResourceManagerUtils.getContainerAppsApiManager(management, subscriptionId)
                ),
                subscriptionId, resourceGroupName, serviceName, region, tier);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getBuildLogs")
    public @ResponseBody ResponseEntity<?> getBuildLogs(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String appName, @RequestParam String githubAction) {
        try {
            String buildLogs = asaStandardTierService.getBuildLogs(
                new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
                ),
                subscriptionId, resourceGroupName, serviceName, appName,
                null, githubAction);
            return new ResponseEntity<>(buildLogs, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/deploy")
    public @ResponseBody ResponseEntity<?> deployGithubRepoSourceCodeToASA(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String appName, @RequestParam String module, @RequestParam String javaVersion, @RequestParam String relativePath) {
        try {
            asaStandardTierService.deploy(
                new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
                ),
                subscriptionId,
                resourceGroupName,
                serviceName,
                appName,
                DeploymentParameter.buildJarParameters(module, javaVersion, relativePath)
            );
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getBuildSourceResult")
    public @ResponseBody ResponseEntity<?> getBuildSourceResult(
        @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
        @RequestParam String subscriptionId,
        @RequestParam String resourceGroupName,
        @RequestParam String serviceName,
        @RequestParam String appName
    ) {
        try {
            String status = buildService.checkBuildSourceStatus(management, subscriptionId, resourceGroupName, serviceName, appName);
            if (STATUS_FAILED.equals(status)) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            } else {
                return new ResponseEntity<>(HttpStatus.OK);
            }
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
