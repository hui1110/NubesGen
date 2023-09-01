package io.github.nubesgen.web.azure.springapps;

import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appplatform.models.ResourceUploadDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nubesgen.model.azure.Region;
import io.github.nubesgen.model.azure.ResourceGroup;
import io.github.nubesgen.model.azure.Subscription;
import io.github.nubesgen.model.azure.springapps.JavaMavenProject;
import io.github.nubesgen.model.azure.springapps.ServiceInstance;
import io.github.nubesgen.service.azure.springapps.AzureResourceManagerService;
import io.github.nubesgen.service.azure.springapps.DeploymentManager;
import io.github.nubesgen.service.azure.springapps.StandardTierService;
import io.github.nubesgen.service.github.GitHubActionService;
import io.github.nubesgen.utils.AzureResourceManagerUtils;
import io.github.nubesgen.utils.JavaProjectUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.azure.core.util.polling.implementation.PollingConstants.STATUS_SUCCEEDED;
import static io.github.nubesgen.service.azure.springapps.Constants.DEFAULT_OAUTH2_CLIENT;

@RestController
@RequestMapping("/springapps")
public class SpringAppController {
    
    private final StandardTierService standardTierService;
    private final AzureResourceManagerService azureResourceManagerService;
    private final GitHubActionService asaGitHubActionService;

    public SpringAppController(StandardTierService standardTierService, AzureResourceManagerService azureResourceManagerService, GitHubActionService asaGitHubActionService) {
        this.standardTierService = standardTierService;
        this.azureResourceManagerService = azureResourceManagerService;
        this.asaGitHubActionService = asaGitHubActionService;
    }

    @GetMapping("/getAppNameAndJavaVersion")
    public @ResponseBody ResponseEntity<?> getAppNameAndJavaVersion(
            @RequestParam() String url,
            @RequestParam(required=false) String branchName,
            @RequestParam(required=false) String module
    ) {
        try {
            JavaMavenProject javaMavenProject = JavaProjectUtils.getNameAndJavaVersion(url, branchName, module);
            return new ResponseEntity<>(javaMavenProject, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getSubscriptionList")
    public @ResponseBody ResponseEntity<?> getSubscriptionList(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management
    ) {
        try {
            List<Subscription> subscriptionInstanceList = azureResourceManagerService.getSubscriptionList(new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, null)
            ));
            return new ResponseEntity<>(subscriptionInstanceList, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getResourceGroupList")
    public @ResponseBody ResponseEntity<?> getResourceGroupList(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
            @RequestParam String subscriptionId
    ) {
        try {
            List<ResourceGroup> resourceGroupInstances = azureResourceManagerService.getResourceGroupList(new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
            ));
            return new ResponseEntity<>(resourceGroupInstances, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getServiceInstanceList")
    public @ResponseBody ResponseEntity<?> getServiceInstanceList(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
            @RequestParam String subscriptionId,
            @RequestParam String resourceGroupName
    ) {
        try {
            List<ServiceInstance> asaInstanceList = azureResourceManagerService.getServiceinstanceList(new DeploymentManager(
                            AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
                    ),
                    resourceGroupName);
            return new ResponseEntity<>(asaInstanceList, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getRegionList")
    public @ResponseBody ResponseEntity<?> getRegionList() {
        try {
            List<Region> regionInstances = azureResourceManagerService.getRegionList();
            return new ResponseEntity<>(regionInstances, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/checkAppExist")
    public @ResponseBody ResponseEntity<?> checkAppExist(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
            @RequestParam String subscriptionId,
            @RequestParam String resourceGroupName,
            @RequestParam String serviceName,
            @RequestParam String appName
    ){
        try {
            boolean res = standardTierService.checkAppExist(new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
            ), resourceGroupName, serviceName, appName);
            return new ResponseEntity<>(res, HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/provisionResourceGroup")
    public @ResponseBody ResponseEntity<?> provisionResourceGroup(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
            @RequestParam String subscriptionId,
            @RequestParam String resourceGroupName,
            @RequestParam String region
    ){
        try {
            azureResourceManagerService.provisionResourceGroup(new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
            ), resourceGroupName, region);
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/provisionSpringApp")
    public @ResponseBody ResponseEntity<?> provisionSpringService(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
            @RequestParam String subscriptionId,
            @RequestParam String resourceGroupName,
            @RequestParam String serviceName,
            @RequestParam String appName
    ){
        try {
            standardTierService.provisionSpringApp(new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
            ), resourceGroupName, serviceName, appName);
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/createDeploymentForApp")
    public @ResponseBody ResponseEntity<?> createDeploymentForApp(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
            @RequestBody Map<String, Object> map
    ) {
        try {
            String subscriptionId = String.valueOf(map.get("subscriptionId"));
            String resourceGroupName = String.valueOf(map.get("resourceGroupName"));
            String serviceName = String.valueOf(map.get("serviceName"));
            String appName = String.valueOf(map.get("appName"));
            String cpu = String.valueOf(map.get("cpu"));
            String memory = String.valueOf(map.get("memory"));
            String instanceCount = String.valueOf(map.get("instanceCount"));
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> variablesMap = objectMapper.readValue(map.get("variables").toString(), new TypeReference<>() {
            });
            standardTierService.createDeploymentForApp(new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
            ), resourceGroupName, serviceName, appName, cpu, memory, instanceCount, variablesMap);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getUploadUrl")
    public @ResponseBody ResponseEntity<?> getUploadUrl(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
            @RequestParam String subscriptionId,
            @RequestParam String resourceGroupName,
            @RequestParam String serviceName,
            @RequestParam String appName
    ){
        try {
            ResourceUploadDefinition res = standardTierService.getUploadUrl(new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
            ), resourceGroupName, serviceName, appName);
            return new ResponseEntity<>(res, HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/uploadFile")
    public @ResponseBody ResponseEntity<?> uploadFile(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
            @RequestBody Map<String,String> map
    ){
        try {
            String subscriptionId = map.get("subscriptionId");
            String uploadUrl = map.get("uploadUrl");
            String url = map.get("url");
            String branchName = map.get("branchName");
            standardTierService.uploadFile(new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
            ), uploadUrl, url, branchName);
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/checkWorkFlowFile")
    public @ResponseBody ResponseEntity<?> checkWorkFlowFile(
            @RequestParam String url,
            @RequestParam String branchName,
            @RequestParam String tier
    ){
        try {
            Map<String, String> res = asaGitHubActionService.checkWorkFlowFile(url, branchName, tier);
            return new ResponseEntity<>(res, HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/createCredentials")
    public @ResponseBody ResponseEntity<?> createCredentials(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
            @RequestParam String subscriptionId,
            @RequestParam String appName,
            @RequestParam String url,
            @RequestParam String branchName
    ){
        try {
            String tenantId = AzureResourceManagerUtils.getTenantId(management, subscriptionId);
            String oAuth2ClientId = management.getClientRegistration().getClientId();
            String oAuth2ClientSecret = management.getClientRegistration().getClientSecret();
            String clientId = asaGitHubActionService.createCredentials(new DeploymentManager(
                    AzureResourceManagerUtils.getAzureResourceManager(management, subscriptionId)), appName,
                    url, branchName, tenantId, oAuth2ClientId, oAuth2ClientSecret, subscriptionId);
            return new ResponseEntity<>(clientId, HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/pushSecretsToGitHub")
    public @ResponseBody ResponseEntity<?> pushSecretsToGitHub(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
            @RequestParam String subscriptionId,
            @RequestParam String resourceGroupName,
            @RequestParam String serviceName,
            @RequestParam String appName,
            @RequestParam String url,
            @RequestParam String clientId,
            @RequestParam String code,
            @RequestParam String tier
    ){
        try {
            String tenantId = AzureResourceManagerUtils.getTenantId(management, subscriptionId);
            String accessToken = asaGitHubActionService.pushSecretsToGitHub(tenantId, subscriptionId, resourceGroupName,
                    serviceName, appName, url, clientId, code, tier);
            return new ResponseEntity<>(accessToken, HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/generateWorkflowFile")
    public @ResponseBody ResponseEntity<?> generateWorkflowFile(
            @RequestParam String url,
            @RequestParam String branchName,
            @RequestParam String module,
            @RequestParam String javaVersion,
            @RequestParam String accessToken,
            @RequestParam String tier,
            @RequestParam String pathName
    ){
        try {
            asaGitHubActionService.generateWorkflowFile(pathName, url, branchName, module, javaVersion, accessToken, tier);
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getDeployResultAndApplicationLogs")
    public @ResponseBody ResponseEntity<?> getApplicationLogs(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
            @RequestParam String subscriptionId,
            @RequestParam String resourceGroupName,
            @RequestParam String serviceName,
            @RequestParam String appName
    ){
        try {
            DeploymentManager deploymentManager = new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
            );
            String status = standardTierService.checkDeployStatus(deploymentManager, resourceGroupName, serviceName, appName);
            String res = standardTierService.getApplicationLogs(deploymentManager, resourceGroupName, serviceName, appName, status);
            if (STATUS_SUCCEEDED.equals(status)) {
                return new ResponseEntity<>(res, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(res, HttpStatus.ACCEPTED);
            }
        }catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/deleteApp")
    public @ResponseBody ResponseEntity<?> deleteApp(
            @RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management,
            @RequestParam String subscriptionId,
            @RequestParam String resourceGroupName,
            @RequestParam String serviceName,
            @RequestParam String appName
    ){
        try {
            standardTierService.deleteApp(new DeploymentManager(
                    AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId)
            ), resourceGroupName, serviceName, appName);
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
