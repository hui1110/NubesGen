package io.github.nubesgen.web.azure.springapps;

import io.github.nubesgen.service.azure.springapps.DeploymentManager;
import io.github.nubesgen.service.azure.springapps.ConsumptionTierService;
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
@RequestMapping("/springapps/standardgen2")
public class StandardGen2Controller {

    private final ConsumptionTierService asaStandardGen2Service;

    public StandardGen2Controller(ConsumptionTierService asaStandardGen2Service) {
        this.asaStandardGen2Service = asaStandardGen2Service;
    }

    @GetMapping("/provisionSpringService")
    public @ResponseBody ResponseEntity<?> provisionSpringService(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
            @RequestParam String subscriptionId,
            @RequestParam String resourceGroupName,
            @RequestParam String serviceName,
            @RequestParam String region,
            @RequestParam String tier
    ) {
        try {
            asaStandardGen2Service.provisionSpringService(
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
}
