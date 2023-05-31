package io.github.nubesgen.web;

import io.github.nubesgen.model.ASAInstance;
import io.github.nubesgen.model.AppInstance;
import io.github.nubesgen.model.ProjectInstance;
import io.github.nubesgen.model.RegionInstance;
import io.github.nubesgen.model.ResourceGrooupInstance;
import io.github.nubesgen.model.SubscriptionInstance;
import io.github.nubesgen.service.AzureDeployService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AzureDeployController {

    private final AzureDeployService azureDeployService;
    private static final String DEFAULT_OAUTH2_CLIENT = "management";

    AzureDeployController(AzureDeployService azureDeployService){
        this.azureDeployService = azureDeployService;
    }

    @GetMapping("/subscriptionList")
    public @ResponseBody ResponseEntity<?> getSubscriptionList(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management) {
        try{
            List<SubscriptionInstance> subscriptionInstanceList = azureDeployService.getSubscriptionList(management);
            return new ResponseEntity<>(subscriptionInstanceList, HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getResourceGroups/{subscriptionId}")
    public @ResponseBody ResponseEntity<?> getResourceGroupList(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @PathVariable String subscriptionId) {
        try{
            List<ResourceGrooupInstance> resourceGroupInstances = azureDeployService.getResourceGroupList(management, subscriptionId);
            return new ResponseEntity<>(resourceGroupInstances, HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/instanceList/{subscriptionId}/{rgName}")
    public @ResponseBody ResponseEntity<?> getServiceInstanceList(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @PathVariable String subscriptionId, @PathVariable String rgName) {
        try {
            List<ASAInstance> asaInstanceList = azureDeployService.getServiceinstanceList(management, subscriptionId, rgName);
            return new ResponseEntity<>(asaInstanceList, HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getAppNameAndJavaVersion")
    public @ResponseBody ResponseEntity<?> getAppNameAndJavaVersion(@RequestParam String url, @RequestParam String branchName, @RequestParam String module){
        try{
            ProjectInstance projectInstance = azureDeployService.getNameAndJavaVersion(url, branchName, module);
            return new ResponseEntity<>(projectInstance, HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/deploy")
    public @ResponseBody ResponseEntity<?> deployGithubRepoSourceCodeToASA(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, HttpServletRequest request) {
        try {
            String url = request.getParameter("url");
            String subscriptionId = request.getParameter("subscriptionId");
            String rgName = request.getParameter("rgName");
            String serviceName = request.getParameter("serviceName");
            String appName = request.getParameter("appName");
            String javaVersion = request.getParameter("javaVersion");
            String regionName = request.getParameter("region");
            String branchName = request.getParameter("branchName");
            String module = request.getParameter("module");
            Integer cpu = Integer.valueOf(request.getParameter("cpu"));
            Integer memory = Integer.valueOf(request.getParameter("memory"));
            Integer instanceCount = Integer.valueOf(request.getParameter("instanceCount"));
            azureDeployService.deploySourceCodeToSpringApps(management, subscriptionId, regionName, rgName, serviceName,
                appName, module, javaVersion, cpu, memory, instanceCount, url, branchName);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/updateActiveDeployment")
    public @ResponseBody ResponseEntity<?> updateActiveDeployment(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String rgName, @RequestParam String serviceName, @RequestParam String appName){
        try {
            azureDeployService.updateActiveDeployment(management, subscriptionId, rgName, serviceName, appName);
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getAppList")
    public List<AppInstance> getAppListFromASAInstance(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String rgName, @RequestParam String serviceName){
        return azureDeployService.getAppList(management, subscriptionId, rgName, serviceName);
    }

    @GetMapping("/getRegionList")
    public List<RegionInstance> getRegionList() {
        return azureDeployService.getRegionList();
    }

}
