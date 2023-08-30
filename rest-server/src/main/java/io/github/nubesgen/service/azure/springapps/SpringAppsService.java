package io.github.nubesgen.service.azure.springapps;

import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.appplatform.fluent.models.AppResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.DeploymentResourceInner;
import com.azure.resourcemanager.appplatform.models.AppResourceProperties;
import com.azure.resourcemanager.appplatform.models.BuildResultUserSourceInfo;
import com.azure.resourcemanager.appplatform.models.DeploymentInstance;
import com.azure.resourcemanager.appplatform.models.DeploymentResourceProperties;
import com.azure.resourcemanager.appplatform.models.DeploymentSettings;
import com.azure.resourcemanager.appplatform.models.JarUploadedUserSourceInfo;
import com.azure.resourcemanager.appplatform.models.ResourceRequests;
import com.azure.resourcemanager.appplatform.models.ResourceUploadDefinition;
import com.azure.resourcemanager.appplatform.models.Sku;
import com.azure.resourcemanager.appplatform.models.SpringApp;
import com.azure.resourcemanager.appplatform.models.SpringAppDeployment;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.storage.file.share.ShareFileAsyncClient;
import com.azure.storage.file.share.ShareFileClientBuilder;
import io.github.nubesgen.model.azure.springapps.JavaMavenProject;
import io.github.nubesgen.model.azure.Region;
import io.github.nubesgen.model.azure.ResourceGroup;
import io.github.nubesgen.model.azure.springapps.ServiceInstance;
import io.github.nubesgen.model.azure.Subscription;
import io.github.nubesgen.utils.AzureResourceManagerUtils;
import io.github.nubesgen.utils.GithubUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
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
import java.util.ArrayList;
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

import static com.azure.core.util.polling.implementation.PollingConstants.STATUS_RUNNING;
import static com.azure.core.util.polling.implementation.PollingConstants.STATUS_SUCCEEDED;
import static io.github.nubesgen.service.azure.springapps.Constants.Enterprise;
import static io.github.nubesgen.service.azure.springapps.Constants.Enterprise_Alias;
import static io.github.nubesgen.service.azure.springapps.Constants.Standard;
import static io.github.nubesgen.service.azure.springapps.Constants.Standard_Alias;

/**
 * Basic operations for Azure Spring Apps
 */
@Service
public class SpringAppsService {

    private final Logger log = LoggerFactory.getLogger(SpringAppsService.class);
    private static final String DEFAULT_DEPLOYMENT_NAME = "default";

    /**
     * Get subscription list.
     *
     * @param management OAuth2 authorization client after login
     * @return the subscription instance list
     */
    public List<Subscription> getSubscriptionList(OAuth2AuthorizedClient management) {
        AppPlatformManager appPlatformManager = AzureResourceManagerUtils.getAppPlatformManager(management, null);
        return appPlatformManager.resourceManager().subscriptions().list().stream().sorted(Comparator.comparing(com.azure.resourcemanager.resources.models.Subscription::displayName))
                .map(subscription -> new Subscription(subscription.subscriptionId(),
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
    public List<ResourceGroup> getResourceGroupList(OAuth2AuthorizedClient management, String subscriptionId) {
        AppPlatformManager appPlatformManager = AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId);
        return appPlatformManager.resourceManager().resourceGroups().list().stream().sorted(Comparator.comparing(com.azure.resourcemanager.resources.models.ResourceGroup::name))
                .map(resourceGroup -> new ResourceGroup(resourceGroup.name()))
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
        AppPlatformManager appPlatformManager = AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId);
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
     * Get project name and java version from pom.xml.
     *
     * @param url repository url
     * @param branchName branch name
     * @param module module name
     * @return project name and java version
     */
    public synchronized JavaMavenProject getNameAndJavaVersion(String url, String branchName, String module) {
        module = Objects.equals(module, "null") ? null : module;
        String pathName = GithubUtils.downloadSourceCodeFromGitHub(url, branchName);
        assert pathName != null;
        Model model;
        if (module == null) {
            try (FileInputStream fis = new FileInputStream(pathName.concat("/pom.xml"))) {
                MavenXpp3Reader reader = new MavenXpp3Reader();
                model = reader.read(fis);
            } catch (Exception e) {
                GithubUtils.deleteRepositoryDirectory(new File(pathName));
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
                GithubUtils.deleteRepositoryDirectory(new File(pathName));
                throw new RuntimeException(e.getMessage());
            }
        }
        GithubUtils.deleteRepositoryDirectory(new File(pathName));
        JavaMavenProject javaMavenProject = new JavaMavenProject();
        if (model.getName() != null) {
            javaMavenProject.setName(model.getName().replaceAll(" ", "").toLowerCase());
        }
        model.getProperties().put("java.version", model.getProperties().getOrDefault("java.version", "11"));
        javaMavenProject.setVersion("Java_".concat(String.valueOf(model.getProperties().get("java.version"))));
        return javaMavenProject;
    }


    /**
     * Provision resource group.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId subscriptionId
     * @param resourceGroupName resourceGroupName
     * @param region region
     */
    public void provisionResourceGroup(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName, String region) {
        AppPlatformManager appPlatformManager = AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId);
        try {
            appPlatformManager.resourceManager().resourceGroups().getByName(resourceGroupName);
            log.info("Resource group {} already exists.", resourceGroupName);
        } catch (Exception e) {
            // provision resource group
            appPlatformManager.resourceManager().resourceGroups().define(resourceGroupName).withRegion(region).create();
            log.info("Resource group {} created.", resourceGroupName);
        }
        }

    /**
     * Provision spring app.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     */
    public void provisionSpringApp(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName,
                                   String serviceName,
                                   String appName) {
        AppPlatformManager appPlatformManager = AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId);
        AppResourceInner appResourceInner = getAppResourceInner();
        appPlatformManager.serviceClient().getApps().createOrUpdate(resourceGroupName, serviceName, appName, appResourceInner);
        log.info("Provision spring app {} completed.", appName);
    }

    /**
     * Get sku.
     *
     * @param tier tier
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
     * Get log by url string.
     *
     * @param strUrl url
     * @param basicAuth basicAuth
     * @return log string
     */
    public String getLogByUrl(String strUrl, String basicAuth) {
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
            return null;
        } finally {
            try {
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e) {
                log.error("Failed to close connection.", e);
            }
        }
    }

    /**
     * Get app resource inner.
     *
     * @return app resource inner
     */
    public static AppResourceInner getAppResourceInner() {
        AppResourceProperties properties = new AppResourceProperties();
        properties.withHttpsOnly(true);
        properties.withPublicProperty(true);

        AppResourceInner appResourceInner = new AppResourceInner();
        appResourceInner.withProperties(properties);
        return appResourceInner;
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
            AppPlatformManager appPlatformManager = AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId);
            SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
            springService.apps().getByName(appName);
        } catch (Exception e) {
            return false;
        }
        return true;
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
        AppPlatformManager appPlatformManager = AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId);
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);

        DeploymentSettings deploymentSettings = new DeploymentSettings()
                .withResourceRequests(new ResourceRequests().withCpu(cpu).withMemory(memory));
        if (!variables.isEmpty()) {
            deploymentSettings.withEnvironmentVariables(variables);
        }

        DeploymentResourceProperties deploymentResourceProperties = new DeploymentResourceProperties();
        deploymentResourceProperties.withActive(true);
        deploymentResourceProperties.withDeploymentSettings(deploymentSettings);
        if (springService.sku().tier().equals("Enterprise")) {
            deploymentResourceProperties.withSource(new BuildResultUserSourceInfo().withBuildResultId("<default>"));
        } else {
            deploymentResourceProperties.withSource(new JarUploadedUserSourceInfo().withRelativePath("<default>"));
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
        AppPlatformManager appPlatformManager = AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId);
        return appPlatformManager.serviceClient().getApps().getResourceUploadUrlAsync(
                resourceGroupName, serviceName, appName).block();
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
        AppPlatformManager appPlatformManager = AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId);
        ShareFileAsyncClient fileClient = new ShareFileClientBuilder()
                .endpoint(uploadUrl)
                .httpClient(appPlatformManager.httpPipeline().getHttpClient())
                .buildFileAsyncClient();
        try {
            String pathName = GithubUtils.downloadSourceCodeFromGitHub(url, branchName);
            File gzFile = createTarGzFile(new File(pathName));
            fileClient.create(gzFile.length())
                    .flatMap(fileInfo -> fileClient.uploadFromFile(gzFile.getAbsolutePath())).retry(2)
                    .block();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        AppPlatformManager appPlatformManager = AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId);
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        String endpoint = getLogStreamingEndpoint(springService, appName);
        String url;
        try {
            if (!STATUS_SUCCEEDED.equals(status)) {
                url = String.format("%s?tailLines=%s&follow=%s", endpoint, 1000, true);
            } else {
                url = String.format("%s?tailLines=%s", endpoint, 1000);
            }
            final String basicAuth = AzureResourceManagerUtils.getAuthorizationCode(springService, appName);
            return getLogByUrl(url, basicAuth);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check deploy status.
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
        AppPlatformManager appPlatformManager = AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId);
        Map<String, String> appStatusMap = getAppAndInstanceStatus(appPlatformManager, resourceGroupName, serviceName, appName);
        String appStatus = appStatusMap.get("appStatus");
        String instanceStatus = appStatusMap.get("instanceStatus");
        //            build succeed, and deploy succeed
        if (STATUS_SUCCEEDED.equals(appStatus) && STATUS_RUNNING.equals(instanceStatus)) {
            return STATUS_SUCCEEDED;
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
        AppPlatformManager appPlatformManager = AzureResourceManagerUtils.getAppPlatformManager(management, subscriptionId);
        SpringService springService = appPlatformManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        springService.apps().deleteByName(appName);
    }

    /**
     * get the application log streaming endpoint.
     */
    private static String getLogStreamingEndpoint(SpringService springService, String appName) {
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

    /**
     * User source code to create tar.gz file.
     */
    private static synchronized File createTarGzFile(File sourceFolder) throws IOException {
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
            throw new RuntimeException(e);
        }
        GithubUtils.deleteRepositoryDirectory(sourceFolder);
        return compressFile;
    }

    /**
     * Get app status and instance status.
     *
     * @param appPlatformManager appPlatformManager
     * @param resourceGroupName resourceGroupName
     * @param serviceName serviceName
     * @param appName appName
     * @return app status and instance status
     */
    public Map<String, String> getAppAndInstanceStatus(AppPlatformManager appPlatformManager, String resourceGroupName,
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

}
