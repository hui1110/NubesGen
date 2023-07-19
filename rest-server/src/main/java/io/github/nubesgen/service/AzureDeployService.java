package io.github.nubesgen.service;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.appplatform.fluent.models.AppResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.BuildInner;
import com.azure.resourcemanager.appplatform.fluent.models.BuildResultInner;
import com.azure.resourcemanager.appplatform.fluent.models.BuildServiceAgentPoolResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.DeploymentResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.ServiceResourceInner;
import com.azure.resourcemanager.appplatform.models.AppResourceProperties;
import com.azure.resourcemanager.appplatform.models.BuildProperties;
import com.azure.resourcemanager.appplatform.models.BuildResultProvisioningState;
import com.azure.resourcemanager.appplatform.models.BuildResultUserSourceInfo;
import com.azure.resourcemanager.appplatform.models.BuildServiceAgentPoolProperties;
import com.azure.resourcemanager.appplatform.models.BuildServiceAgentPoolSizeProperties;
import com.azure.resourcemanager.appplatform.models.DeploymentInstance;
import com.azure.resourcemanager.appplatform.models.DeploymentResourceProperties;
import com.azure.resourcemanager.appplatform.models.DeploymentSettings;
import com.azure.resourcemanager.appplatform.models.JarUploadedUserSourceInfo;
import com.azure.resourcemanager.appplatform.models.ResourceRequests;
import com.azure.resourcemanager.appplatform.models.ResourceUploadDefinition;
import com.azure.resourcemanager.appplatform.models.Sku;
import com.azure.resourcemanager.appplatform.models.SourceUploadedUserSourceInfo;
import com.azure.resourcemanager.appplatform.models.SpringApp;
import com.azure.resourcemanager.appplatform.models.SpringAppDeployment;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceUtils;
import com.azure.resourcemanager.resources.fluentcore.utils.ResourceManagerUtils;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import com.azure.resourcemanager.resources.models.Subscription;
import com.azure.storage.file.share.ShareFileAsyncClient;
import com.azure.storage.file.share.ShareFileClientBuilder;
import io.github.nubesgen.model.ProjectInstance;
import io.github.nubesgen.model.RegionInstance;
import io.github.nubesgen.model.ResourceGrooupInstance;
import io.github.nubesgen.model.ServiceInstance;
import io.github.nubesgen.model.SubscriptionInstance;
import io.github.nubesgen.utils.AzureResourceManagerUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

@Service
public class AzureDeployService {

    private final Logger log = LoggerFactory.getLogger(AzureDeployService.class);
    private static final String DEFAULT_DEPLOYMENT_NAME = "default";
    private static final String STATUS_FAILED = "Failed";
    private static final String STATUS_SUCCEED = "Succeeded";
    private static final String STATUS_RUNNING = "Running";

    /**
     * Get subscription list.
     *
     * @param management OAuth2 authorization client after login
     * @return the subscription instance list
     */
    public List<SubscriptionInstance> getSubscriptionList(OAuth2AuthorizedClient management) {
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, null);
        return appPlatformManager.resourceManager().subscriptions().list().stream().sorted(Comparator.comparing(Subscription::displayName))
                .map(subscription -> new SubscriptionInstance(subscription.subscriptionId(),
                        subscription.displayName()))
                .collect(Collectors.toList());
    }

    /**
     * Get resource group list.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @return the resource group instance list
     */
    public List<ResourceGrooupInstance> getResourceGroupList(OAuth2AuthorizedClient management, String subscriptionId) {
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        return appPlatformManager.resourceManager().resourceGroups().list().stream().sorted(Comparator.comparing(ResourceGroup::name))
                .map(resourceGroup -> new ResourceGrooupInstance(resourceGroup.name()))
                .collect(Collectors.toList());
    }

    /**
     * Get service instance list.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @return Azure Spring Apps instance list
     */
    public List<ServiceInstance> getServiceinstanceList(OAuth2AuthorizedClient management, String subscriptionId,
                                                        String resourceGroupName) {
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        return appPlatformManager.springServices().list().stream().filter(springService -> Objects.equals(springService.resourceGroupName(), resourceGroupName)).sorted(Comparator.comparing(SpringService::name))
                .map(springService -> new ServiceInstance(springService.region(),
                        springService.resourceGroupName(), springService.id(), springService.name(),
                        springService.sku().tier()))
                .collect(Collectors.toList());
    }

    /**
     * Get region list.
     *
     * @return the region list
     */
    public List<RegionInstance> getRegionList() {
        List<Region> regionArrayList = new ArrayList<>(Region.values());
        List<RegionInstance> resList = new ArrayList<>();
        if (!regionArrayList.isEmpty()) {
            for (Region region : regionArrayList) {
                resList.add(new RegionInstance(region.name(), region.label()));
            }
        }
        return resList.stream().sorted(Comparator.comparing(RegionInstance::getName)).collect(Collectors.toList());
    }

    /**
     * Get project name and java version from pom.xml.
     *
     * @param url repository url
     * @param branchName branch name
     * @param module module name
     * @return project name and java version
     */
    public synchronized ProjectInstance getNameAndJavaVersion(String url, String branchName, String module) {
        module = Objects.equals(module, "null") ? null : module;
        String pathName = getRepositoryPath(url, branchName);
        assert pathName != null;
        Model model;
        if (module == null) {
            try (FileInputStream fis = new FileInputStream(pathName.concat("/pom.xml"))) {
                MavenXpp3Reader reader = new MavenXpp3Reader();
                model = reader.read(fis);
            } catch (Exception e) {
                deleteRepositoryDirectory(new File(pathName));
                throw new RuntimeException(e.getMessage());
            }
        } else {
            try (FileInputStream fis = new FileInputStream(pathName.concat("/").concat(module.concat("/pom.xml")))) {
                MavenXpp3Reader reader = new MavenXpp3Reader();
                model = reader.read(fis);
                if (model.getProperties().isEmpty() || !model.getProperties().containsKey("java.version")) {
                    FileInputStream fisParent = new FileInputStream(pathName.concat("/pom.xml"));
                    MavenXpp3Reader readerParent = new MavenXpp3Reader();
                    Properties properties = readerParent.read(fisParent).getProperties();
                    fisParent.close();
                    if (!properties.isEmpty() && properties.containsKey("java.version")) {
                        model.getProperties().put("java.version", properties.getProperty("java.version"));
                    }
                }
            } catch (Exception e) {
                deleteRepositoryDirectory(new File(pathName));
                throw new RuntimeException(e.getMessage());
            }
        }
        deleteRepositoryDirectory(new File(pathName));
        ProjectInstance projectInstance = new ProjectInstance();
        if (model.getName() != null) {
            projectInstance.setName(model.getName().replaceAll(" ", "").toLowerCase());
        }
        model.getProperties().put("java.version", model.getProperties().getOrDefault("java.version", "11"));
        projectInstance.setVersion("Java_".concat(String.valueOf(model.getProperties().get("java.version"))));
        return projectInstance;
    }

    /**
     * Check app exist.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @return true if app exist, otherwise false
     */
    public boolean checkAppExist(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName,
                                 String serviceName,
                                 String appName) {
        try {
            AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
            SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
            springService.apps().getByName(appName);
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Provision resource.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     */
    public void provisionResource(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName,
                                  String serviceName,
                                  String appName) {
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        String tier = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName).sku().tier();

        if (Objects.equals(tier, "Enterprise")) {
            appPlatformManager.serviceClient().getBuildServiceAgentPools()
                    .updatePut(
                            resourceGroupName,
                            serviceName,
                            "default",
                            "default",
                            new BuildServiceAgentPoolResourceInner()
                                    .withProperties(
                                            new BuildServiceAgentPoolProperties()
                                                    .withPoolSize(new BuildServiceAgentPoolSizeProperties().withName("S5")) // S1, S2, S3, S4, S5
                                    )
                    );
            log.info("Initialize build service agent pool for enterprise tier.");
        }

        AppResourceProperties properties = new AppResourceProperties();
        properties.withHttpsOnly(true);
        properties.withPublicProperty(true);

        AppResourceInner appResourceInner = new AppResourceInner();
        appResourceInner.withProperties(properties);

        appPlatformManager.serviceClient().getApps().createOrUpdate(resourceGroupName, serviceName, appName, appResourceInner);
        log.info("Provision spring app {} completed.", appName);
    }

    /**
     * Create deployment for app.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param cpu the cpu
     * @param memory the memory
     * @param instanceCount the instance count
     */
    public void createDeploymentForApp(OAuth2AuthorizedClient management, String subscriptionId,
                                       String resourceGroupName,
                                       String serviceName,
                                       String appName,
                                       String cpu,
                                       String memory,
                                       String instanceCount,
                                       Map<String, String> variables) {
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);

        DeploymentSettings deploymentSettings = new DeploymentSettings()
                .withResourceRequests(new ResourceRequests().withCpu(cpu).withMemory(memory));
        if (!variables.isEmpty()) {
            deploymentSettings.withEnvironmentVariables(variables);
        }

        DeploymentResourceProperties deploymentResourceProperties = new DeploymentResourceProperties();
        deploymentResourceProperties.withActive(true);
        deploymentResourceProperties.withDeploymentSettings(deploymentSettings);
        if (springService.sku().tier().equals("Standard")) {
            deploymentResourceProperties.withSource(new JarUploadedUserSourceInfo().withRelativePath("<default>"));
        } else {
            deploymentResourceProperties.withSource(new BuildResultUserSourceInfo().withBuildResultId("<default>"));
        }

        DeploymentResourceInner resourceInner = new DeploymentResourceInner()
                .withSku(
                        new Sku()
                                .withName(springService.sku().name())
                                // instance count
                                .withCapacity(Integer.valueOf(instanceCount)))
                .withProperties(deploymentResourceProperties);
        appPlatformManager.serviceClient().getDeployments().createOrUpdate(resourceGroupName, serviceName, appName, DEFAULT_DEPLOYMENT_NAME, resourceInner);
        log.info("Successfully created default deployment for app {}.", appName);
    }

    /**
     * Get upload url string.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @return upload url
     */
    public ResourceUploadDefinition getUploadUrl(OAuth2AuthorizedClient management, String subscriptionId,
                                                 String resourceGroupName,
                                                 String serviceName,
                                                 String appName) {
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        ResourceUploadDefinition uploadUrl = appPlatformManager.serviceClient().getApps().getResourceUploadUrlAsync(
                resourceGroupName, serviceName, appName).block();
        log.info("Get the upload url successfully.");
        return uploadUrl;
    }

    /**
     * Upload file.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param url the url
     * @param branchName the branch name
     * @param uploadUrl the upload url
     */
    public void uploadFile(OAuth2AuthorizedClient management, String subscriptionId, String uploadUrl,
                           String url,
                           String branchName) {
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        ShareFileAsyncClient fileClient = new ShareFileClientBuilder()
                .endpoint(uploadUrl)
                .httpClient(appPlatformManager.httpPipeline().getHttpClient())
                .buildFileAsyncClient();
        try {
            String pathName = getRepositoryPath(url, branchName);
            File gzFile = createTarGzFile(new File(pathName));
            fileClient.create(gzFile.length())
                    .flatMap(fileInfo -> fileClient.uploadFromFile(gzFile.getAbsolutePath())).retry(2)
                    .block();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        log.info("Upload file successfully.");
    }

    /**
     * Deploy source code to Azure Spring App instance(Standard).
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param javaVersion the java version
     */
    public void buildAndDeploySourceCodeStandard(OAuth2AuthorizedClient management, String subscriptionId,
                                                 String resourceGroupName,
                                                 String serviceName,
                                                 String appName,
                                                 String module, String javaVersion,
                                                 String relativePath) {
        log.info("Start build and deploy.....");
        try {
            SourceUploadedUserSourceInfo sourceInfo = new SourceUploadedUserSourceInfo();
            sourceInfo.withRuntimeVersion(javaVersion);
            sourceInfo.withRelativePath(relativePath);
            if (!Objects.equals(module, "null")) {
                sourceInfo.withArtifactSelector(module); // withTargetModule
            }
            AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
            // deploy app
            DeploymentResourceInner resourceInner = appPlatformManager.serviceClient().getDeployments().get(resourceGroupName, serviceName, appName,
                    DEFAULT_DEPLOYMENT_NAME);
            resourceInner.properties().withSource(sourceInfo);
            appPlatformManager.serviceClient().getDeployments().update(resourceGroupName, serviceName, appName,
                    DEFAULT_DEPLOYMENT_NAME, resourceInner);
            log.info("Deploy source code to app {} completed.", appName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Enqueue build to Azure Spring App instance(Enterprise).
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param relativePath the relative path
     * @param region the region
     * @param javaVersion the java version
     * @param module the module
     * @return Succeeded, build result id or failed
     */
    public String enqueueBuildEnterprise(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName, String serviceName, String appName, String relativePath, String region, String javaVersion, String module) {
        log.info("Start build.....");
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);

        ServiceResourceInner serviceResourceInner = new ServiceResourceInner()
                .withLocation(region)
                .withSku(springService.sku());
        serviceResourceInner = appPlatformManager.serviceClient().getServices().createOrUpdate(resourceGroupName, serviceName, serviceResourceInner);

        // Build file with source code
        BuildProperties buildProperties = new BuildProperties()
                .withBuilder(String.format("%s/buildservices/%s/builders/%s", serviceResourceInner.id(), "default", "default"))
                .withAgentPool(String.format("%s/buildservices/%s/agentPools/%s", serviceResourceInner.id(), "default", "default"))
                .withRelativePath(relativePath);
        log.info("--------Build file with source code.------------");

        Map<String, String> buildEnv = new HashMap<>();
        // Java version
        buildEnv.put("BP_JVM_VERSION", javaVersion);
        if (!Objects.equals(module, "null")) {
            // target module
            buildEnv.put("BP_MAVEN_BUILT_MODULE", module);
        }
        buildProperties.withEnv(buildEnv);

        // Enqueue build
        BuildInner enqueueResult = appPlatformManager.serviceClient().getBuildServices()
                .createOrUpdateBuild(
                        resourceGroupName,
                        serviceName,
                        DEFAULT_DEPLOYMENT_NAME,
                        appName,
                        new BuildInner().withProperties(buildProperties)
                );
        log.info("--------Enqueue build.------------");
        return enqueueResult.properties().triggeredBuildResult().id();
    }

    /**
     * Build source code to Azure Spring App instance(Enterprise).
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param buildId the build id
     * @return Succeeded or failed
     */
    public String buildSourceEnterprise(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName, String serviceName, String appName, String buildId) {
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);

        // Get build state
        BuildResultInner buildResult = appPlatformManager.serviceClient().getBuildServices()
                .getBuildResult(
                        resourceGroupName,
                        serviceName,
                        DEFAULT_DEPLOYMENT_NAME,
                        appName,
                        ResourceUtils.nameFromResourceId(buildId));
        BuildResultProvisioningState buildState = buildResult.properties().provisioningState();
        log.info("--------Get build state.------------");

        // Wait for build
        while (buildState == BuildResultProvisioningState.QUEUING || buildState == BuildResultProvisioningState.BUILDING) {
            buildResult = appPlatformManager.serviceClient().getBuildServices()
                    .getBuildResult(
                            resourceGroupName,
                            serviceName,
                            DEFAULT_DEPLOYMENT_NAME,
                            appName,
                            ResourceUtils.nameFromResourceId(buildId));
            buildState = buildResult.properties().provisioningState();
            ResourceManagerUtils.sleep(Duration.ofSeconds(30));
        }
        log.info("--------Build completed.------------");
        return buildState.toString();
    }


    /**
     * Deploy source code to Azure Spring App instance(Enterprise).
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param buildId the build id
     */
    public void deploySourceCodeEnterprise(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName, String serviceName, String appName, String buildId) {
        log.info("Start deploy.....");
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);

        BuildResultUserSourceInfo sourceInfo = new BuildResultUserSourceInfo();
        sourceInfo.withBuildResultId(buildId);

        DeploymentResourceInner resourceInner = appPlatformManager.serviceClient().getDeployments().get(resourceGroupName, serviceName, appName,
                DEFAULT_DEPLOYMENT_NAME);
        resourceInner.properties().withSource(sourceInfo);
        appPlatformManager.serviceClient().getDeployments().update(resourceGroupName, serviceName, appName,
                DEFAULT_DEPLOYMENT_NAME, resourceInner);
        log.info("Deploy source code to app {} completed.", appName);
    }

    /**
     * Get build source log.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @return null: no log generated, otherwise the build log
     */
    public String getBuildLogs(OAuth2AuthorizedClient management, String subscriptionId,
                               String resourceGroupName,
                               String serviceName, String appName, String stage) {
        String buildLogs = null;
        try {
            AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
            SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
            if (springService.sku().tier().equals("Standard")) {
                SpringAppDeployment springAppDeployment = springService.apps().getByName(appName).getActiveDeployment();
                if(Objects.isNull(springAppDeployment) || Objects.isNull(springAppDeployment.getLogFileUrl())) {
                    return null;
                }
                buildLogs = getLogByUrl(springAppDeployment.getLogFileUrl(), null);
            } else {
                BuildInner buildInner = appPlatformManager.serviceClient().getBuildServices().getBuild(resourceGroupName, serviceName, DEFAULT_DEPLOYMENT_NAME, appName);
                if(buildInner.properties().provisioningState().toString().equals(BuildResultProvisioningState.QUEUING.toString())) {
                  return BuildResultProvisioningState.QUEUING.toString();
                }
                final String endpoint = springService.apps().getByName(appName).parent().listTestKeys().primaryTestEndpoint();
                final String logEndpoint = String.format("%s/api/logstream/buildpods/%s.%s-build-%s-build-pod/stages/%s?follow=true", endpoint.replace(".test", ""), DEFAULT_DEPLOYMENT_NAME, appName, ResourceUtils.nameFromResourceId(buildInner.properties().triggeredBuildResult().id()), stage);
                final String basicAuth = getAuthorizationCode(springService, appName);
                buildLogs = getLogByUrl(logEndpoint, basicAuth);
            }
        } catch (Exception e) {
            log.error("Get build log failed, error: " + e.getMessage());
        }
        return buildLogs;
    }

    /**
     * Get application log.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @return none: no log generated, otherwise the application log
     */
    public String getApplicationLogs(OAuth2AuthorizedClient management, String subscriptionId,
                                     String resourceGroupName,
                                     String serviceName, String appName, String status) {
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        String endpoint = getLogStreamingEndpoint(springService, appName);
        String url;
        try {
            if(!STATUS_SUCCEED.equals(status)) {
                url = String.format("%s?tailLines=%s&follow=%s", endpoint, 1000, true);
            } else {
                url = String.format("%s?tailLines=%s", endpoint, 1000);
            }
            final String basicAuth = getAuthorizationCode(springService, appName);
            return getLogByUrl(url, basicAuth);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check build source status(Standard).
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @return failed: Failed, null:  build source done
     */
    public String checkBuildSourceStatus(OAuth2AuthorizedClient management, String subscriptionId,
                                         String resourceGroupName,
                                         String serviceName, String appName) {
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        Map<String, String> appStatusMap = getAppAndInstanceStatus(appPlatformManager, resourceGroupName, serviceName, appName);
        String appStatus = appStatusMap.get("appStatus");
        String instanceStatus = appStatusMap.get("instanceStatus");
        //        build source failed
        if (STATUS_FAILED.equals(appStatus) && STATUS_RUNNING.equals(instanceStatus)) {
            return STATUS_FAILED;
        }
        return null;
    }

    /**
     * Check deploy status(Standard).
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @return failed: Failed, succeed: Succeeded
     */
    public String checkDeployStatus(OAuth2AuthorizedClient management, String subscriptionId,
                                    String resourceGroupName,
                                    String serviceName, String appName) {
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        Map<String, String> appStatusMap = getAppAndInstanceStatus(appPlatformManager, resourceGroupName, serviceName, appName);
        String appStatus = appStatusMap.get("appStatus");
        String instanceStatus = appStatusMap.get("instanceStatus");
        //            build succeed, and deploy succeed
        if (STATUS_SUCCEED.equals(appStatus) && STATUS_RUNNING.equals(instanceStatus)) {
            return STATUS_SUCCEED;
        }
        return null;
    }

    /**
     * Delete app.
     *
     * @param management OAuth2 authorization client after login
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     */
    public void deleteApp(OAuth2AuthorizedClient management, String subscriptionId,
                          String resourceGroupName,
                          String serviceName, String appName) {
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        springService.apps().deleteByName(appName);
    }


    //     Get Authorization code
    private String getAuthorizationCode(SpringService springService, String appName) {
        final String password = springService.apps().getByName(appName).parent().listTestKeys().primaryKey();
        final String userPass = "primary:" + password;
        return "Basic " + new String(Base64.getEncoder().encode(userPass.getBytes()));
    }

    //    Get build log by file url
    private String getLogByUrl(String strUrl, String basicAuth) {
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(strUrl);
            connection = (HttpsURLConnection) url.openConnection();
            if (basicAuth != null) {
                connection.setRequestProperty("Authorization", basicAuth);
            }
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            byte[] getData = connection.getInputStream().readAllBytes();
            return new String(getData);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        } finally {
            try {
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //    Get the application log streaming endpoint
    private String getLogStreamingEndpoint(SpringService springService, String appName) {
        return Optional.ofNullable(springService.apps().getByName(appName)).map(SpringApp::activeDeploymentName).map(d -> {
            final String endpoint = springService.apps().getByName(appName).parent().listTestKeys().primaryTestEndpoint();
            List<DeploymentInstance> deploymentInstances =
                    springService.apps().getByName(appName).getActiveDeployment().instances();
            deploymentInstances =
                    deploymentInstances.stream().sorted(Comparator.comparing(DeploymentInstance::startTime)).collect(Collectors.toList());
            String instanceName = deploymentInstances.get(deploymentInstances.size() - 1).name();
            return String.format("%s/api/logstream/apps/%s/instances/%s", endpoint.replace(".test", ""), appName,
                    instanceName);
        }).orElse(null);
    }

    //    Download the source code from the git repository
    private synchronized String getRepositoryPath(String url, String branchName) {
        String repositoryPath = url.substring(url.lastIndexOf("/") + 1);
        deleteRepositoryDirectory(new File(repositoryPath));
        branchName = Objects.equals(branchName, "null") ? null : branchName;
        Git git = null;
        String pathName = null;
        try {
            git = Git.cloneRepository()
                    .setURI(url)
                    .setBranch(branchName)
                    .call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (git != null) {
                git.close();
                pathName = git.getRepository().getWorkTree().toString();
            }
        }
        return pathName;
    }

    //    Create a tar.gz file
    private synchronized File createTarGzFile(File sourceFolder) throws IOException {
        File compressFile = File.createTempFile("java_package", "tar.gz");
        compressFile.deleteOnExit();
        try (TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(
                new GZIPOutputStream(new FileOutputStream(compressFile)));
             Stream<Path> paths = Files.walk(sourceFolder.toPath())) {
            tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            for (Path sourceFile : paths.toList()) {
                String relativePath = sourceFolder.toPath().relativize(sourceFile).toString();
                TarArchiveEntry entry = new TarArchiveEntry(sourceFile.toFile(), relativePath);
                if (sourceFile.toFile().isFile()) {
                    try (InputStream inputStream = new FileInputStream(sourceFile.toFile())) {
                        tarArchiveOutputStream.putArchiveEntry(entry);
                        IOUtils.copy(inputStream, tarArchiveOutputStream);
                        tarArchiveOutputStream.closeArchiveEntry();
                    }
                } else {
                    tarArchiveOutputStream.putArchiveEntry(entry);
                    tarArchiveOutputStream.closeArchiveEntry();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        deleteRepositoryDirectory(sourceFolder);
        return compressFile;
    }

    //    Deploy complete, delete temp directory
    private synchronized void deleteRepositoryDirectory(File directory) {
        File tempGitDirectory;
        try {
            tempGitDirectory = new File(directory.toString());
            if (tempGitDirectory.exists()) {
                FileUtils.deleteDirectory(tempGitDirectory);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //    Get app status and instance status, check build and deploy status
    private Map<String, String> getAppAndInstanceStatus(AppPlatformManager appPlatformManager, String resourceGroupName,
                                                        String serviceName, String appName) {
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        SpringAppDeployment springAppDeployment = springService.apps().getByName(appName).getActiveDeployment();
        String appStatus = springAppDeployment.innerModel().properties().provisioningState().toString();

        List<DeploymentInstance> deploymentInstances = springAppDeployment.instances();
        deploymentInstances = deploymentInstances.stream().sorted(Comparator.comparing(DeploymentInstance::startTime)).collect(Collectors.toList());
        String instanceStatus = deploymentInstances.get(deploymentInstances.size() - 1).status();

        Map<String, String> appAndInstanceStatus = new HashMap<>();
        appAndInstanceStatus.put("appStatus", appStatus);
        appAndInstanceStatus.put("instanceStatus", instanceStatus);
        return appAndInstanceStatus;
    }

    private AppPlatformManager getAppPlatformManager(OAuth2AuthorizedClient management, String subscriptionId) {
//        final TokenCredential credential = AzureResourceManagerUtils.toTokenCredential(management.getAccessToken().getTokenValue());

        TokenCredential tokenCredential = new DefaultAzureCredentialBuilder().build();
        TokenRequestContext request = new TokenRequestContext().addScopes("https://management.azure.com/.default");
        AccessToken token =
                tokenCredential.getToken(request).retry(3L).blockOptional().orElseThrow(() -> new RuntimeException(
                        "Couldn't retrieve JWT"));
        final TokenCredential credential = AzureResourceManagerUtils.toTokenCredential(token.getToken());

        final AzureProfile azureProfile = new AzureProfile(AzureEnvironment.AZURE);
        AzureResourceManager.Authenticated authenticated = AzureResourceManager.authenticate(credential, azureProfile);
        if(authenticated.subscriptions().list().stream().findAny().isEmpty()){
            throw new RuntimeException("No subscription found !");
        }
        if (subscriptionId == null) {
            subscriptionId = authenticated.subscriptions().list().iterator().next().subscriptionId();
        }
        final String currentSubscriptionId = subscriptionId;
        Subscription subscription = authenticated.subscriptions().list().stream().filter(s ->s.subscriptionId().equals(currentSubscriptionId)).toList().get(0);
        final AzureProfile profile = new AzureProfile(subscription.innerModel().tenantId(), subscriptionId, AzureEnvironment.AZURE);
        return AppPlatformManager.authenticate(credential, profile);
    }

}
