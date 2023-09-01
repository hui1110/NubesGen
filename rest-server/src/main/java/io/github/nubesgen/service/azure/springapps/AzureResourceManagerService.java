package io.github.nubesgen.service.azure.springapps;

import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.appplatform.fluent.models.AppResourceInner;
import com.azure.resourcemanager.appplatform.models.AppResourceProperties;
import com.azure.resourcemanager.appplatform.models.Sku;
import com.azure.resourcemanager.appplatform.models.SpringService;
import io.github.nubesgen.model.azure.Region;
import io.github.nubesgen.model.azure.ResourceGroup;
import io.github.nubesgen.model.azure.Subscription;
import io.github.nubesgen.model.azure.springapps.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.github.nubesgen.service.azure.springapps.Constants.Enterprise;
import static io.github.nubesgen.service.azure.springapps.Constants.Enterprise_Alias;
import static io.github.nubesgen.service.azure.springapps.Constants.Standard;
import static io.github.nubesgen.service.azure.springapps.Constants.Standard_Alias;

/**
 * Providing operations for Azure Resource Manager.
 */
@Service
public class AzureResourceManagerService {

    private final Logger log = LoggerFactory.getLogger(AzureResourceManagerService.class);
    /**
     * Get subscription list.
     *
     * @param deploymentManager ARM client.
     * @return the subscription instance list.
     */
    public List<Subscription> getSubscriptionList(DeploymentManager deploymentManager) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();
        return appPlatformManager.resourceManager().subscriptions().list().stream().sorted(Comparator.comparing(com.azure.resourcemanager.resources.models.Subscription::displayName))
                .map(subscription -> new Subscription(subscription.subscriptionId(),
                        subscription.displayName()))
                .collect(Collectors.toList());
    }

    /**
     * Get resource group list.
     *
     * @param deploymentManager ARM client.
     * @return the resource group instance list
     */
    public List<ResourceGroup> getResourceGroupList(DeploymentManager deploymentManager) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();
        return appPlatformManager.resourceManager().resourceGroups().list().stream().sorted(Comparator.comparing(com.azure.resourcemanager.resources.models.ResourceGroup::name))
                .map(resourceGroup -> new ResourceGroup(resourceGroup.name()))
                .collect(Collectors.toList());
    }

    /**
     * Get service instance list.
     *
     * @param deploymentManager ARM client.
     * @param resourceGroupName the resource group name.
     * @return Azure Spring Apps instance list
     */
    public List<ServiceInstance> getServiceinstanceList(DeploymentManager deploymentManager,
                                                        String resourceGroupName) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();
        return appPlatformManager.springServices().list().stream().filter(springService -> Objects.equals(springService.resourceGroupName(), resourceGroupName)).sorted(Comparator.comparing(SpringService::name))
                .map(springService -> new ServiceInstance(springService.region(),
                        springService.resourceGroupName(), springService.id(), springService.name(),
                        springService.sku().tier()))
                .collect(Collectors.toList());
    }

    /**
     * Get region list.
     *
     * @return the region list.
     */
    public List<Region> getRegionList() {
        List<com.azure.core.management.Region> regionArrayList = new ArrayList<>(com.azure.core.management.Region.values());
        List<Region> resList = new ArrayList<>();
        if (!regionArrayList.isEmpty()) {
            for (com.azure.core.management.Region region : regionArrayList) {
                resList.add(new Region(region.name(), region.label()));
            }
        }
        return resList.stream().sorted(Comparator.comparing(Region::getName)).collect(Collectors.toList());
    }

    /**
     * Get sku.
     *
     * @param tier the tier name
     */
    public Sku getSku(String tier) {
        if(Objects.equals(tier, Standard)){
            return new Sku().withName(Standard_Alias).withTier(tier);
        }else if(Objects.equals(tier, Enterprise)){
            return new Sku().withName(Enterprise_Alias).withTier(tier);
        }else {
            return new Sku().withName(Standard_Alias).withTier(tier);
        }
    }

    /**
     * Get app resource inner.
     *
     * @return app resource inner
     */
    public AppResourceInner getAppResourceInner() {
        AppResourceProperties properties = new AppResourceProperties();
        properties.withHttpsOnly(true);
        properties.withPublicProperty(true);

        AppResourceInner appResourceInner = new AppResourceInner();
        appResourceInner.withProperties(properties);
        return appResourceInner;
    }

    /**
     * Provision resource group.
     *
     * @param deploymentManager ARM client.
     * @param resourceGroupName the resource group name.
     * @param region the region name
     */
    public void provisionResourceGroup(DeploymentManager deploymentManager, String resourceGroupName, String region) {
        AppPlatformManager appPlatformManager = deploymentManager.getAppPlatformManager();
        try {
            boolean result = appPlatformManager.resourceManager().resourceGroups().contain(resourceGroupName);
            if(result){
                log.info("Resource group {} already exists.", resourceGroupName);
            }else {
                appPlatformManager.resourceManager().resourceGroups().define(resourceGroupName).withRegion(region).create();
                log.info("Resource group {} created.", resourceGroupName);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
