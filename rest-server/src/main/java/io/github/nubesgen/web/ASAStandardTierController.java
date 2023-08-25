package io.github.nubesgen.web;

import io.github.nubesgen.service.ASAStandardTierService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/standard")
public class ASAStandardTierController {

    private static final String DEFAULT_OAUTH2_CLIENT = "management";
    private final ASAStandardTierService asaStandardTierService;

    public ASAStandardTierController(ASAStandardTierService asaStandardTierService) {
        this.asaStandardTierService = asaStandardTierService;
    }

    @GetMapping("/provisionSpringService")
    public @ResponseBody ResponseEntity<?> provisionSpringApp(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String region, @RequestParam String tier) {
        try {
            asaStandardTierService.provisionSpringService(management, subscriptionId, resourceGroupName, serviceName, region, tier);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getBuildLogs")
    public @ResponseBody ResponseEntity<?> getBuildLogs(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String appName, @RequestParam String githubAction) {
        try {
            String buildLogs = asaStandardTierService.getBuildLogs(management, subscriptionId, resourceGroupName, serviceName, appName, null, githubAction);
            return new ResponseEntity<>(buildLogs, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/deploy")
    public @ResponseBody ResponseEntity<?> deployGithubRepoSourceCodeToASA(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String appName, @RequestParam String module, @RequestParam String javaVersion, @RequestParam String relativePath) {
        try {
            asaStandardTierService.buildAndDeploySourceCode(management, subscriptionId, resourceGroupName,
                    serviceName, appName, module, javaVersion, relativePath);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getBuildSourceResult")
    public @ResponseBody ResponseEntity<?> getBuildSourceResult(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String appName) {
        try {
            String status = asaStandardTierService.checkBuildSourceStatus(management, subscriptionId, resourceGroupName, serviceName, appName);
            if ("Failed".equals(status)) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            } else {
                return new ResponseEntity<>(HttpStatus.OK);
            }
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
