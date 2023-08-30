package io.github.nubesgen.web;

import io.github.nubesgen.model.azure.springapps.DeploymentParameter;
import io.github.nubesgen.service.azure.springapps.DeploymentManager;
import io.github.nubesgen.service.azure.springapps.EnterpriseTierService;
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

import static io.github.nubesgen.service.azure.springapps.Constants.DEFAULT_OAUTH2_CLIENT;


@RestController
@RequestMapping("/springapps/enterprise")
public class ASAEnterpriseTierController {

    private final EnterpriseTierService asaEnterpriseTierService;
    private final BuildService buildService;

    public ASAEnterpriseTierController(EnterpriseTierService asaEnterpriseTierService, BuildService buildService) {
        this.asaEnterpriseTierService = asaEnterpriseTierService;
        this.buildService = buildService;
    }

    @GetMapping("/provisionSpringService")
    public @ResponseBody ResponseEntity<?> provisionSpringApp(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String region, @RequestParam String tier) {
        try {
            asaEnterpriseTierService.provision(
                new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
                ),
                subscriptionId,
                resourceGroupName,
                serviceName,
                region,
                tier
            );
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getBuildLogs")
    public @ResponseBody ResponseEntity<?> getBuildLogs(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String appName, @RequestParam(required = false) String stage, @RequestParam(required = false) String githubAction) {
        try {
            String buildLogs = asaEnterpriseTierService.getBuildLogs(
                new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
                ),
                subscriptionId,
                resourceGroupName,
                serviceName,
                appName,
                stage,
                githubAction
            );
            return new ResponseEntity<>(buildLogs, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

        @GetMapping("/enqueueBuild")
    public @ResponseBody ResponseEntity<?> enqueueBuildEnterprise(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String appName, @RequestParam String relativePath, @RequestParam String region, @RequestParam String javaVersion, @RequestParam String module) {
        try {
            String res = buildService.enqueueBuild(management, subscriptionId, resourceGroupName, serviceName, appName, relativePath, region, javaVersion, module);
            return new ResponseEntity<>(res, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/buildSourceCode")
    public @ResponseBody ResponseEntity<?> buildSourceEnterprise(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String appName, @RequestParam String buildId) {
        try {
            String res = buildService.buildSourceCode(management, subscriptionId, resourceGroupName, serviceName, appName, buildId);
            return new ResponseEntity<>(res, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/deploy")
    public @ResponseBody ResponseEntity<?> deployAppEnterprise(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String appName, @RequestParam String buildId){
        try {
            asaEnterpriseTierService.deploy(
                new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
                ),
                subscriptionId,
                resourceGroupName,
                serviceName,
                appName,
                DeploymentParameter.buildBuildResultParameters(buildId)
            );
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
