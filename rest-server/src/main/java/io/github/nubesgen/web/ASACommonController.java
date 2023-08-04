package io.github.nubesgen.web;

import com.azure.resourcemanager.appplatform.models.ResourceUploadDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nubesgen.model.ProjectInstance;
import io.github.nubesgen.model.RegionInstance;
import io.github.nubesgen.model.ResourceGrooupInstance;
import io.github.nubesgen.model.ServiceInstance;
import io.github.nubesgen.model.SubscriptionInstance;
import io.github.nubesgen.service.ASACommonService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ASACommonController {

    private static final String DEFAULT_OAUTH2_CLIENT = "management";
    private final ASACommonService  asaCommonService;

    public ASACommonController(ASACommonService asaCommonService) {
        this.asaCommonService = asaCommonService;
    }

    @GetMapping("/getAppNameAndJavaVersion")
    public @ResponseBody ResponseEntity<?> getAppNameAndJavaVersion(@RequestParam() String url,
                                                                    @RequestParam(required=false) String branchName,
                                                                    @RequestParam(required=false) String module) {
        try {
            ProjectInstance projectInstance = asaCommonService.getNameAndJavaVersion(url, branchName, module);
            return new ResponseEntity<>(projectInstance, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getSubscriptionList")
    public @ResponseBody ResponseEntity<?> getSubscriptionList(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management) {
        try {
            List<SubscriptionInstance> subscriptionInstanceList = asaCommonService.getSubscriptionList(management);
            return new ResponseEntity<>(subscriptionInstanceList, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getResourceGroupList")
    public @ResponseBody ResponseEntity<?> getResourceGroupList(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId) {
        try {
            List<ResourceGrooupInstance> resourceGroupInstances = asaCommonService.getResourceGroupList(management,
                    subscriptionId);
            return new ResponseEntity<>(resourceGroupInstances, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getServiceInstanceList")
    public @ResponseBody ResponseEntity<?> getServiceInstanceList(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName) {
        try {
            List<ServiceInstance> asaInstanceList = asaCommonService.getServiceinstanceList(management, subscriptionId,
                    resourceGroupName);
            return new ResponseEntity<>(asaInstanceList, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getRegionList")
    public @ResponseBody ResponseEntity<?> getRegionList() {
        try {
            List<RegionInstance> regionInstances = asaCommonService.getRegionList();
            return new ResponseEntity<>(regionInstances, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/checkAppExist")
    public @ResponseBody ResponseEntity<?> checkAppExist(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String appName){
        try {
            boolean res = asaCommonService.checkAppExist(management, subscriptionId, resourceGroupName, serviceName, appName);
            return new ResponseEntity<>(res, HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/provisionResourceGroup")
    public @ResponseBody ResponseEntity<?> provisionResourceGroup(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String region){
        try {
            asaCommonService.provisionResourceGroup(management, subscriptionId, resourceGroupName, region);
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/provisionSpringService")
    public @ResponseBody ResponseEntity<?> provisionSpringService(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String region, @RequestParam String tier){
        try {
            asaCommonService.provisionSpringService(management, subscriptionId, resourceGroupName, serviceName, region, tier);
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/createDeploymentForApp")
    public @ResponseBody ResponseEntity<?> createDeploymentForApp(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestBody Map<String, Object> map) {
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
            asaCommonService.createDeploymentForApp(management, subscriptionId, resourceGroupName, serviceName, appName, cpu, memory, instanceCount, variablesMap);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getUploadUrl")
    public @ResponseBody ResponseEntity<?> getUploadUrl(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String appName){
        try {
            ResourceUploadDefinition res = asaCommonService.getUploadUrl(management, subscriptionId, resourceGroupName, serviceName, appName);
            return new ResponseEntity<>(res, HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/uploadFile")
    public @ResponseBody ResponseEntity<?> uploadFile(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestBody Map<String,String> map){
        try {
            String subscriptionId = map.get("subscriptionId");
            String uploadUrl = map.get("uploadUrl");
            String url = map.get("url");
            String branchName = map.get("branchName");
            asaCommonService.uploadFile(management, subscriptionId, uploadUrl, url, branchName);
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }



    @GetMapping("/getDeployResultAndApplicationLogs")
    public @ResponseBody ResponseEntity<?> getApplicationLogs(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String appName){
        try {
            String status = asaCommonService.checkDeployStatus(management, subscriptionId, resourceGroupName, serviceName, appName);
            String res = asaCommonService.getApplicationLogs(management, subscriptionId, resourceGroupName, serviceName, appName, status);
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
    public @ResponseBody ResponseEntity<?> deleteApp(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String resourceGroupName, @RequestParam String serviceName, @RequestParam String appName){
        try {
            asaCommonService.deleteApp(management, subscriptionId, resourceGroupName, serviceName, appName);
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
