package io.github.nubesgen.web;

import com.azure.resourcemanager.appplatform.models.ResourceUploadDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nubesgen.model.azure.springapps.JavaMavenProject;
import io.github.nubesgen.model.azure.Region;
import io.github.nubesgen.model.azure.ResourceGroup;
import io.github.nubesgen.model.azure.springapps.ServiceInstance;
import io.github.nubesgen.model.azure.Subscription;
import io.github.nubesgen.service.azure.springapps.SpringAppsService;
import io.github.nubesgen.service.github.GitHubActionService;
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

import static io.github.nubesgen.service.azure.springapps.Constants.DEFAULT_OAUTH2_CLIENT;

@RestController
@RequestMapping("/springapps")
public class SpringController {

    private final SpringAppsService springService;
    private final GitHubActionService asaGitHubActionService;

    public SpringController(SpringAppsService springService, GitHubActionService asaGitHubActionService) {
        this.springService = springService;
        this.asaGitHubActionService = asaGitHubActionService;
    }

    @GetMapping("/getAppNameAndJavaVersion")
    public @ResponseBody ResponseEntity<?> getAppNameAndJavaVersion(
        @RequestParam() String url,
        @RequestParam(required=false) String branchName,
        @RequestParam(required=false) String module
    ) {
        try {
            JavaMavenProject javaMavenProject = springService.getNameAndJavaVersion(url, branchName, module);
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
            List<Subscription> subscriptionInstanceList = springService.getSubscriptionList(management);
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
            List<ResourceGroup> resourceGroupInstances = springService.getResourceGroupList(management,
                    subscriptionId);
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
            List<ServiceInstance> asaInstanceList = springService.getServiceinstanceList(management, subscriptionId,
                    resourceGroupName);
            return new ResponseEntity<>(asaInstanceList, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getRegionList")
    public @ResponseBody ResponseEntity<?> getRegionList() {
        try {
            List<Region> regionInstances = springService.getRegionList();
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
            boolean res = springService.checkAppExist(management, subscriptionId, resourceGroupName, serviceName, appName);
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
            springService.provisionResourceGroup(management, subscriptionId, resourceGroupName, region);
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
            springService.provisionSpringApp(management, subscriptionId, resourceGroupName, serviceName, appName);
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
            springService.createDeploymentForApp(management, subscriptionId, resourceGroupName, serviceName, appName, cpu, memory, instanceCount, variablesMap);
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
            ResourceUploadDefinition res = springService.getUploadUrl(management, subscriptionId, resourceGroupName, serviceName, appName);
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
            springService.uploadFile(management, subscriptionId, uploadUrl, url, branchName);
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
            boolean res = asaGitHubActionService.checkWorkFlowFile(url, branchName, tier);
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
//            String clientId = asaGitHubActionService.createCredentials(management, subscriptionId, appName, url, branchName);
            return new ResponseEntity<>("clientId", HttpStatus.OK);
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
            String accessToken = asaGitHubActionService.pushSecretsToGitHub(management, subscriptionId, resourceGroupName, serviceName, appName, url, clientId, code, tier);
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
        @RequestParam String tier
    ){
        try {
            asaGitHubActionService.generateWorkflowFile(url, branchName, module, javaVersion, accessToken, tier);
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
            String status = springService.checkDeployStatus(management, subscriptionId, resourceGroupName, serviceName, appName);
            String res = springService.getApplicationLogs(management, subscriptionId, resourceGroupName, serviceName, appName, status);
            if ("Succeeded".equals(status)) {
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
            springService.deleteApp(management, subscriptionId, resourceGroupName, serviceName, appName);
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
