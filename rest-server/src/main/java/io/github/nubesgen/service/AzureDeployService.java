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
import com.azure.resourcemanager.appplatform.fluent.models.DeploymentResourceInner;
import com.azure.resourcemanager.appplatform.fluent.models.ServiceResourceInner;
import com.azure.resourcemanager.appplatform.models.AppResourceProperties;
import com.azure.resourcemanager.appplatform.models.DeploymentInstance;
import com.azure.resourcemanager.appplatform.models.DeploymentResourceProperties;
import com.azure.resourcemanager.appplatform.models.DeploymentSettings;
import com.azure.resourcemanager.appplatform.models.ResourceRequests;
import com.azure.resourcemanager.appplatform.models.ResourceUploadDefinition;
import com.azure.resourcemanager.appplatform.models.Sku;
import com.azure.resourcemanager.appplatform.models.SourceUploadedUserSourceInfo;
import com.azure.resourcemanager.appplatform.models.SpringApp;
import com.azure.resourcemanager.appplatform.models.SpringAppDeployment;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import com.azure.resourcemanager.resources.models.Subscription;
import com.azure.storage.file.share.ShareFileAsyncClient;
import com.azure.storage.file.share.ShareFileClientBuilder;
import io.github.nubesgen.model.ProjectInstance;
import io.github.nubesgen.model.RegionInstance;
import io.github.nubesgen.model.ResourceGrooupInstance;
import io.github.nubesgen.model.ServiceInstance;
import io.github.nubesgen.model.SubscriptionInstance;
import io.github.nubesgen.utils.ResourceManagerUtils;
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
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
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

    /**
     * Get subscription list.
     *
     * @param management OAuth2 authorization client after login
     * @return the subscription instance list
     */
    public List<SubscriptionInstance> getSubscriptionList(OAuth2AuthorizedClient management) {
        AzureResourceManager arm = getAzureResourceManager(management, null);
        return arm.subscriptions().list().stream().sorted(Comparator.comparing(Subscription::displayName))
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
        AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
        return azureResourceManager.resourceGroups().list().stream().sorted(Comparator.comparing(ResourceGroup::name))
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
        AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
        return azureResourceManager.springServices().list().stream().filter(springService -> Objects.equals(springService.resourceGroupName(), resourceGroupName)).sorted(Comparator.comparing(SpringService::name))
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
                e.printStackTrace();
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
                    Assert.isTrue(properties.isEmpty() || properties.containsKey("java.version"), "Please configure "
                        + "the java version in the pom.xml file of module a or parent.");
                    model.getProperties().put("java.version", properties.getProperty("java.version"));
                }
            } catch (Exception e) {
                e.printStackTrace();
                deleteRepositoryDirectory(new File(pathName));
                throw new RuntimeException(e.getMessage());
            }
        }
        deleteRepositoryDirectory(new File(pathName));
        ProjectInstance projectInstance = new ProjectInstance();
//        Assert.isTrue(model.getName() != null, "Project name is not configured in pom.xml");
        Assert.isTrue(model.getProperties().containsKey("java.version"), "Java version is not configured in pom"
            + ".xml");
        if(model.getName() != null) {
            projectInstance.setName(model.getName().replaceAll(" ", "").toLowerCase());
        }
        String version = String.valueOf(model.getProperties().get("java.version"));
        projectInstance.setVersion(Objects.equals(version, "null") ? null : "Java_" + version);
        return projectInstance;
    }

    /**
     * Provision resource.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param regionName the region name
     * @return the app details string
     */
    public String provisionResource(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName,
                                    String serviceName,
                                    String appName, String regionName) {
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        ServiceResourceInner serviceResourceInner = new ServiceResourceInner()
            .withLocation(regionName)
            .withSku(new Sku().withName("S0"));

        // provision spring service
        appPlatformManager.serviceClient().getServices().createOrUpdate(resourceGroupName, serviceName, serviceResourceInner);
        log.info("provision spring service done");

        // provision spring app
        AppResourceProperties properties = new AppResourceProperties();
        properties.withHttpsOnly(true);
        properties.withPublicProperty(true);

        AppResourceInner appResourceInner = new AppResourceInner();
        appResourceInner.withProperties(properties);

        appPlatformManager.serviceClient().getApps().createOrUpdate(resourceGroupName, serviceName, appName, appResourceInner);
        AppResourceInner appResourceInner1 = appPlatformManager.serviceClient().getApps().get(resourceGroupName, serviceName, appName);
        log.info("provision spring app done");
        return ResourceManagerUtils.getAppResourceInner(appResourceInner1);
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
                                String appName){
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        ResourceUploadDefinition uploadUrl = appPlatformManager.serviceClient().getApps().getResourceUploadUrlAsync(
            resourceGroupName, serviceName, appName).block();
        log.info("get upload url done");
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
                             String branchName){
        System.out.println("uploadUrl: " + uploadUrl);
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        ShareFileAsyncClient fileClient = new ShareFileClientBuilder()
            .endpoint(uploadUrl)
            .httpClient(appPlatformManager.httpPipeline().getHttpClient())
            .buildFileAsyncClient();
        String pathName = getRepositoryPath(url, branchName);
        try {
            File gzFile = createTarGzFile(new File(pathName));
            fileClient.create(gzFile.length())
                      .flatMap(fileInfo -> fileClient.uploadFromFile(gzFile.getAbsolutePath()))
                      .block();
        }catch (IOException e){
            e.printStackTrace();
        }
        log.info("upload file done");
    }

    /**
     * Deploy source code to Azure Spring App instance.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param javaVersion the java version
     */
    public void deploySourceCodeToSpringApps(OAuth2AuthorizedClient management, String subscriptionId,
                                             String resourceGroupName,
                                             String serviceName,
                                             String appName,
                                             String regionName,
                                             String module, String javaVersion, String cpu,
                                             String memory,
                                             Integer instanceCount, String relativePath) {
        log.info("Start deploy.....");
        AppPlatformManager appPlatformManager = getAppPlatformManager(management, subscriptionId);
        ServiceResourceInner serviceResourceInner = new ServiceResourceInner()
            .withLocation(regionName)
            .withSku(new Sku().withName("S0"));

        // deploy app
        DeploymentResourceInner resourceInner = new DeploymentResourceInner()
            .withSku(
                new Sku()
                    .withName(serviceResourceInner.sku().name())
                    .withCapacity(instanceCount)); // instance count
        resourceInner.withProperties(new DeploymentResourceProperties());

        SourceUploadedUserSourceInfo sourceInfo = new SourceUploadedUserSourceInfo();
        sourceInfo.withRuntimeVersion(javaVersion);
        sourceInfo.withRelativePath(relativePath);
        if (!Objects.equals(module, "null")) {
            sourceInfo.withArtifactSelector(module); // withTargetModule
        }
        resourceInner.properties()
                     .withActive(true)
                     .withSource(sourceInfo)
                     .withDeploymentSettings(
                         new DeploymentSettings()
                             .withResourceRequests(new ResourceRequests().withCpu(cpu).withMemory(memory)));
        appPlatformManager.serviceClient().getDeployments().createOrUpdate(resourceGroupName, serviceName, appName,
            DEFAULT_DEPLOYMENT_NAME, resourceInner);
        log.info("Deploy app done");
    }

    /**
     * Get build source log.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @return none: no log generated, otherwise the build log
     */
    public String getBuildLogs(OAuth2AuthorizedClient management, String subscriptionId,
                               String resourceGroupName,
                               String serviceName, String appName) {
        AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
        SpringService service = azureResourceManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        SpringAppDeployment springAppDeployment = service.apps().getByName(appName).getActiveDeployment();
        if(Objects.isNull(springAppDeployment) || Objects.isNull(springAppDeployment.getLogFileUrl())) {
            return null;
        }
        String buildLogs = null;
        try {
            buildLogs = getLogByFileUrl(springAppDeployment.getLogFileUrl());
        }catch (Exception e){
            log.error(e.getMessage());
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
        AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
        SpringService springService = azureResourceManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        String endpoint = getLogStreamingEndpoint(springService, appName);
        if (Objects.isNull(endpoint)) {
            return null;
        }
        HttpsURLConnection connection = null;
        URL url;
        try {
            if(!STATUS_SUCCEED.equals(status)) {
                url = new URL(String.format("%s?tailLines=%s&follow=%s", endpoint, 1000, true));
            } else {
                url = new URL(String.format("%s?tailLines=%s", endpoint, 1000));
            }
            final String password = springService.apps().getByName(appName).parent().listTestKeys().primaryKey();
            final String userPass = "primary:" + password;
            final String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userPass.getBytes()));
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", basicAuth);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            byte[] getData = connection.getInputStream().readAllBytes();
            if(!STATUS_SUCCEED.equals(status)) {
                springService.apps().deleteByName(appName);
            }
            return new String(getData);
        } catch (Exception e) {
            e.printStackTrace();
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

    /**
     * Check build source status.
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
        AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
        SpringService springService = azureResourceManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        String status = null;
        SpringAppDeployment springAppDeployment = springService.apps().getByName(appName).getActiveDeployment();
        //            build failed
        if (Objects.isNull(springAppDeployment.instances())) {
            springService.apps().deleteByName(appName);
            status = STATUS_FAILED;
        }
        return status;
    }

    /**
     * Check deploy status.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @return failed: Failed, succeed: Succeeded, Starting: build source done
     */
    public String checkDeployStatus(OAuth2AuthorizedClient management, String subscriptionId,
                                    String resourceGroupName,
                                    String serviceName, String appName) {
        AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
        SpringService springService = azureResourceManager.springServices().getByResourceGroup(resourceGroupName,
            serviceName);
        String status = null;
        SpringAppDeployment springAppDeployment = springService.apps().getByName(appName).getActiveDeployment();
        String appStatus = springAppDeployment.innerModel().properties().provisioningState().toString();

        List<DeploymentInstance> deploymentInstances = springAppDeployment.instances();
        deploymentInstances = deploymentInstances.stream().sorted(Comparator.comparing(DeploymentInstance::startTime)).collect(Collectors.toList());
        String instanceStatus = deploymentInstances.get(deploymentInstances.size() - 1).status();

        System.out.println("appStatus: " + appStatus);
        System.out.println("instanceStatus: " + instanceStatus);
        //            build succeed, but deploy failed
        if (STATUS_SUCCEED.equals(appStatus) && "Running".equals(instanceStatus)) {
            status = STATUS_SUCCEED;
        }
        return status;
    }

    private String getLogByFileUrl(String strUrl) {
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(strUrl);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            byte[] getData = connection.getInputStream().readAllBytes();
            return new String(getData);
        } catch (Exception e) {
            e.printStackTrace();
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
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            if (git != null) {
                git.close();
                pathName = git.getRepository().getWorkTree().toString();
            }
        }
        return pathName;
    }

    private File createTarGzFile(File sourceFolder) throws IOException {
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
            throw new RuntimeException(e);
        }
        deleteRepositoryDirectory(sourceFolder);
        return compressFile;
    }

    private synchronized void deleteRepositoryDirectory(File directory) {
        File tempGitDirectory;
        try {
            tempGitDirectory = new File(directory.toString());
            if (tempGitDirectory.exists()) {
                FileUtils.deleteDirectory(tempGitDirectory);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private AzureResourceManager getAzureResourceManager(OAuth2AuthorizedClient management, String subscriptionId) {
        if (subscriptionId == null) {
            return ResourceManagerUtils.getResourceManager(management.getAccessToken().getTokenValue());
        } else {
            return ResourceManagerUtils.getResourceManager(management.getAccessToken().getTokenValue(), subscriptionId);
        }
    }

    private static TokenCredential toTokenCredential(String accessToken) {
        return request -> Mono.just(new AccessToken(accessToken, OffsetDateTime.MAX));
    }

    private AppPlatformManager getAppPlatformManager(OAuth2AuthorizedClient management, String subscriptionId){
               final TokenCredential credential = toTokenCredential(management.getAccessToken().getTokenValue());
//        TokenRequestContext request = new TokenRequestContext().addScopes("https://management.azure.com/.default");
//        TokenCredential tokenCredential = new DefaultAzureCredentialBuilder().build();
//        AccessToken token = tokenCredential.getToken(request).retry(3L).blockOptional().orElseThrow(() -> new RuntimeException("Couldn't retrieve JWT"));
//        final TokenCredential credential = toTokenCredential(token.getToken());
        final AzureProfile azureProfile = new AzureProfile(AzureEnvironment.AZURE);
        AzureResourceManager authenticated = AzureResourceManager.authenticate(credential, azureProfile).withSubscription(subscriptionId);
        String tenantId = authenticated.tenants().list().iterator().next().tenantId();
        final AzureProfile profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);
        return AppPlatformManager.authenticate(credential, profile);
    }

}
