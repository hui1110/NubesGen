package io.github.nubesgen.web;

import com.azure.core.management.Region;
import com.azure.resourcemanager.appplatform.models.RuntimeVersion;
import io.github.nubesgen.model.ASAInstance;
import io.github.nubesgen.model.AppInstance;
import io.github.nubesgen.model.ProjectInstance;
import io.github.nubesgen.model.RegionInstance;
import io.github.nubesgen.model.ResourceGrooupInstance;
import io.github.nubesgen.model.SubscriptionInstance;
import io.github.nubesgen.service.AzureDeployService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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
    public List<SubscriptionInstance> subscriptionList(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management) {
        return azureDeployService.subscriptionList(management);
    }

    @GetMapping("/instanceList/{subscriptionId}/{rgName}")
    public List<ASAInstance> instanceList(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @PathVariable String subscriptionId, @PathVariable String rgName) {
        return azureDeployService.instanceList(management, subscriptionId, rgName);
    }

    @GetMapping("/deployApp")
    public String deployGithubRepoToASA(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, HttpServletRequest request) {
        String res = null;
        String url = request.getParameter("url");
        String subscriptionId = request.getParameter("subscriptionId");
        String rgName = request.getParameter("rgName");
        String serviceName = request.getParameter("serviceName");
        String appName = request.getParameter("appName");
        String javaVersion = request.getParameter("javaVersion");
        String regionName = request.getParameter("region");
        String branchName = request.getParameter("branchName");
        Integer cpu = Integer.valueOf(request.getParameter("cpu"));
        Integer memory = Integer.valueOf(request.getParameter("memory"));
        Integer instanceCount = Integer.valueOf(request.getParameter("instanceCount"));

        Region region = Region.fromName(regionName);
        RuntimeVersion runtimeVersion = azureDeployService.getJavaVersion(javaVersion);

        String pathName = azureDeployService.getRepositoryPath(url, branchName);

        try {
            res = azureDeployService.deploySourceCodeToSpringApps(management, subscriptionId, region, rgName, serviceName,
                appName, runtimeVersion, pathName, cpu, memory, instanceCount);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }

    @GetMapping("/updateActiveDeployment")
    public boolean updateActiveDeployment(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String rgName, @RequestParam String serviceName, @RequestParam String appName){
        return azureDeployService.updateActiveDeployment(management, subscriptionId, rgName, serviceName, appName);
    }

    @GetMapping("/getAppNameAndJavaVersion")
    public ProjectInstance getAppNameAndJavaVersion(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String url, @RequestParam String branchName){
        return azureDeployService.getNameAndJavaVersion(url, branchName);
    }

    @GetMapping("/getAppList")
    public List<AppInstance> getAppListFromASAInstance(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String rgName, @RequestParam String serviceName){
        return azureDeployService.appList(management, rgName, serviceName, subscriptionId);
    }

    @GetMapping("/getRegionList")
    public List<RegionInstance> getRegionList() {
        return azureDeployService.RegionList();
    }

    @GetMapping("/getResourceGroups/{subscriptionId}")
    public List<ResourceGrooupInstance> getResourceGroups(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @PathVariable String subscriptionId) {
        return azureDeployService.ResourceGroup(management,subscriptionId);
    }

}
