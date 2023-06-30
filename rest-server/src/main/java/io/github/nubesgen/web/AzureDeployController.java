package io.github.nubesgen.web;

import com.azure.resourcemanager.appplatform.models.ResourceUploadDefinition;
import io.github.nubesgen.model.ProjectInstance;
import io.github.nubesgen.model.RegionInstance;
import io.github.nubesgen.model.ResourceGrooupInstance;
import io.github.nubesgen.model.ServiceInstance;
import io.github.nubesgen.model.SubscriptionInstance;
import io.github.nubesgen.service.AzureDeployService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AzureDeployController {

    private final AzureDeployService azureDeployService;
    private static final String DEFAULT_OAUTH2_CLIENT = "management";

    AzureDeployController(AzureDeployService azureDeployService) {
        this.azureDeployService = azureDeployService;
    }

    @GetMapping("/getAppNameAndJavaVersion")
    public @ResponseBody ResponseEntity<?> getAppNameAndJavaVersion(@RequestParam String url,
                                                                    @RequestParam String branchName,
                                                                    @RequestParam String module) {
        try {
            ProjectInstance projectInstance = azureDeployService.getNameAndJavaVersion(url, branchName, module);
            return new ResponseEntity<>(projectInstance, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getSubscriptionList")
    public @ResponseBody ResponseEntity<?> getSubscriptionList(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management) {
        try {
            List<SubscriptionInstance> subscriptionInstanceList = azureDeployService.getSubscriptionList(management);
            return new ResponseEntity<>(subscriptionInstanceList, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getResourceGroupList/{subscriptionId}")
    public @ResponseBody ResponseEntity<?> getResourceGroupList(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @PathVariable String subscriptionId) {
        try {
            List<ResourceGrooupInstance> resourceGroupInstances = azureDeployService.getResourceGroupList(management,
                subscriptionId);
            return new ResponseEntity<>(resourceGroupInstances, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getServiceInstanceList/{subscriptionId}/{resourceGroupName}")
    public @ResponseBody ResponseEntity<?> getServiceInstanceList(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @PathVariable String subscriptionId, @PathVariable String resourceGroupName) {
        try {
            List<ServiceInstance> asaInstanceList = azureDeployService.getServiceinstanceList(management, subscriptionId,
                resourceGroupName);
            return new ResponseEntity<>(asaInstanceList, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/provisionResource/{subscriptionId}/{resourceGroupName}/{serviceName}/{appName}/{region}")
    public @ResponseBody ResponseEntity<?> provisionResource(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @PathVariable String subscriptionId, @PathVariable String resourceGroupName, @PathVariable String serviceName, @PathVariable String appName, @PathVariable String region) {
        try {
            String res = azureDeployService.provisionResource(management, subscriptionId, resourceGroupName, serviceName, appName, region);
            return new ResponseEntity<>(res, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getUploadUrl/{subscriptionId}/{resourceGroupName}/{serviceName}/{appName}")
    public @ResponseBody ResponseEntity<?> getUploadUrl(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @PathVariable String subscriptionId, @PathVariable String resourceGroupName, @PathVariable String serviceName, @PathVariable String appName){
        try {
            ResourceUploadDefinition res = azureDeployService.getUploadUrl(management, subscriptionId, resourceGroupName, serviceName, appName);
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
            azureDeployService.uploadFile(management, subscriptionId, uploadUrl, url, branchName);
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/deploy")
    public @ResponseBody ResponseEntity<?> deployGithubRepoSourceCodeToASA(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, HttpServletRequest request) {
        try {
            String module = request.getParameter("module");
            String subscriptionId = request.getParameter("subscriptionId");
            String resourceGroupName = request.getParameter("resourceGroupName");
            String serviceName = request.getParameter("serviceName");
            String appName = request.getParameter("appName");
            String javaVersion = request.getParameter("javaVersion");
            String regionName = request.getParameter("region");
            String cpu = String.valueOf(request.getParameter("cpu"));
            String memory = request.getParameter("memory");
            Integer instanceCount = Integer.valueOf(request.getParameter("instanceCount"));
            String relativePath = request.getParameter("relativePath");
            azureDeployService.deploySourceCodeToSpringApps(management, subscriptionId, resourceGroupName
                , serviceName, appName, regionName, module, javaVersion, cpu, memory, instanceCount, relativePath);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getBuildSourceResult/{subscriptionId}/{resourceGroupName}/{serviceName}/{appName}")
    public @ResponseBody ResponseEntity<?> getBuildSourceResult(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @PathVariable String subscriptionId, @PathVariable String resourceGroupName, @PathVariable String serviceName, @PathVariable String appName) {
        String status = azureDeployService.checkBuildSourceStatus(management, subscriptionId, resourceGroupName, serviceName, appName);
        if ("Failed".equals(status)) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } else {
            return new ResponseEntity<>(HttpStatus.OK);
        }
    }

    @GetMapping("/getBuildLogs/{subscriptionId}/{resourceGroupName}/{serviceName}/{appName}")
    public @ResponseBody ResponseEntity<?> getBuildLogs(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @PathVariable String subscriptionId, @PathVariable String resourceGroupName, @PathVariable String serviceName, @PathVariable String appName) {
        try {
            String buildLogs = azureDeployService.getBuildLogs(management, subscriptionId, resourceGroupName, serviceName, appName);
            return new ResponseEntity<>(buildLogs, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getApplicationLog/{subscriptionId}/{resourceGroupName}/{serviceName}/{appName}")
    public @ResponseBody ResponseEntity<?> getApplicationLog(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @PathVariable String subscriptionId, @PathVariable String resourceGroupName, @PathVariable String serviceName, @PathVariable String appName) {
        String status = azureDeployService.checkDeployStatus(management, subscriptionId, resourceGroupName, serviceName, appName);
        String res = azureDeployService.getApplicationLogs(management, subscriptionId, resourceGroupName, serviceName, appName, status);
        if("Succeeded".equals(status)) {
            return new ResponseEntity<>(res, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(res, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getRegionList")
    public List<RegionInstance> getRegionList() {
        return azureDeployService.getRegionList();
    }

}
