package io.github.nubesgen.service;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.management.Region;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appplatform.models.DeploymentInstance;
import com.azure.resourcemanager.appplatform.models.RuntimeVersion;
import com.azure.resourcemanager.appplatform.models.SpringApp;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import com.azure.resourcemanager.resources.models.Subscription;
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
    private static final String STATUS_STARTING = "Starting";
    private static final String STATUS_FAILED = "Failed";
    private static final String STATUS_RUNNING = "Running";
    private static final String STATUS_SUCCEED = "Succeeded";
    private static final String STATUS_UPLOAD = "Uploading";

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
        if (model != null) {
            Assert.isTrue(model.getName() != null, "Project name is not configured in pom.xml");
            Assert.isTrue(model.getProperties().containsKey("java.version"), "Java version is not configured in pom"
                + ".xml");
            projectInstance.setName(model.getName().replaceAll(" ", "").toLowerCase());
            String version = String.valueOf(model.getProperties().get("java.version"));
            projectInstance.setVersion(Objects.equals(version, "null") ? null : "Java_" + version);
        }
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
        AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
        SpringService service = azureResourceManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        Region region = Region.fromName(regionName);
        if (!azureResourceManager.resourceGroups().contain(resourceGroupName)) {
            createResourceGroup(azureResourceManager, resourceGroupName, region);
        } else {
            log.info("Resource Group " + resourceGroupName + " already exists.");
        }
        if (azureResourceManager.springServices().checkNameAvailability(serviceName, region).nameAvailable()) {
            createASA(azureResourceManager, resourceGroupName, serviceName, region);
        } else {
            log.info("Azure Spring Apps " + serviceName + " already exists.");
        }
        service.apps().define(appName).withDefaultActiveDeployment().withDefaultPublicEndpoint().withHttpsOnly().create();
        return ResourceManagerUtils.getAppDetails(service.apps().getByName(appName));
    }

    /**
     * Get upload source code result.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @return none: undone, otherwise the log file url
     */
    public String getUploadSourceCodeResult(OAuth2AuthorizedClient management, String subscriptionId,
                                            String resourceGroupName,
                                            String serviceName,
                                            String appName) {
        AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
        SpringService service = azureResourceManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        String result = service.apps().getByName(appName).getActiveDeployment().getLogFileUrl();
        return result == null ? STATUS_UPLOAD : STATUS_SUCCEED;
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
                               String serviceName, String appName, String status) {
        AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
        SpringService service = azureResourceManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        String buildLog = getLogByFileUrl(service.apps().getByName(appName).getActiveDeployment().getLogFileUrl());
        if (STATUS_FAILED.equals(status)) {
             service.apps().deleteByName(appName);
         }
        return buildLog;
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
        SpringService service = azureResourceManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        String endpoint = getLogStreamingEndpoint(service, appName);
        if (Objects.isNull(endpoint)) {
            return null;
        }
        HttpsURLConnection connection = null;
        URL url;
        try {
            if(STATUS_FAILED.equals(status)) {
                url = new URL(String.format("%s?tailLines=%s&follow=%s", endpoint, 1000, true));
            } else {
                url = new URL(String.format("%s?tailLines=%s", endpoint, 1000));
            }
            final String password = service.apps().getByName(appName).parent().listTestKeys().primaryKey();
            final String userPass = "primary:" + password;
            final String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userPass.getBytes()));
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", basicAuth);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            byte[] getData = connection.getInputStream().readAllBytes();
            if(STATUS_FAILED.equals(status)) {
                service.apps().deleteByName(appName);
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
        SpringService springService = azureResourceManager.springServices().getByResourceGroup(resourceGroupName, serviceName);
        String status = null;
        List<DeploymentInstance> deploymentInstances = springService.apps().getByName(appName).getActiveDeployment().instances();
        deploymentInstances = deploymentInstances.stream().sorted(Comparator.comparing(DeploymentInstance::startTime)).collect(Collectors.toList());
        String instanceStatus = deploymentInstances.get(deploymentInstances.size() - 1).status();
        String appStatus = springService.apps().getByName(appName).getActiveDeployment().innerModel().properties().provisioningState().toString();
        if (STATUS_FAILED.equals(instanceStatus) || STATUS_FAILED.equals(appStatus)) {
            status = STATUS_FAILED;
        } else if (STATUS_SUCCEED.equals(appStatus) && STATUS_RUNNING.equals(instanceStatus)) {
            status = STATUS_SUCCEED;
        } else if (STATUS_STARTING.equals(instanceStatus)) {
            status = STATUS_STARTING;
        }
        return status;
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
     * @param url the repository url
     * @param branchName the branch name
     */
    public void deploySourceCodeToSpringApps(OAuth2AuthorizedClient management, String subscriptionId,
                                             String resourceGroupName,
                                             String serviceName,
                                             String appName,
                                             String module, String javaVersion, Integer cpu,
                                             Integer memory,
                                             Integer instanceCount, String url, String branchName) throws IOException {
        module = Objects.equals(module, "null") ? null : module;
        String pathName = getRepositoryPath(url, branchName);
        RuntimeVersion runtimeVersion = getJavaVersion(javaVersion);
        File sourceCode = createTarGzFile(new File(pathName));
        AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
        deployToSpringApp(azureResourceManager, resourceGroupName, serviceName, appName, module, runtimeVersion,
                sourceCode, cpu, memory, instanceCount);
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

    private void createResourceGroup(AzureResourceManager azureResourceManager, String rgName, Region region) {
        log.info("Creating resource group {}", rgName);
        azureResourceManager.resourceGroups().define(rgName)
                            .withRegion(region)
                            .create();
        log.info("Created resource group {}", rgName);
    }

    private void createASA(AzureResourceManager azureResourceManager, String rgName, String serviceName,
                           Region region) {
        log.info("Creating Azure Spring Apps {} in resource group {}", serviceName, rgName);
        SpringService service = azureResourceManager.springServices().define(serviceName)
                                                    .withRegion(region)
                                                    .withExistingResourceGroup(rgName)
                                                    .create();
        ResourceManagerUtils.print(service);
        log.info("Created Azure Spring Apps {} ", service.name());
    }

    private void deployToSpringApp(AzureResourceManager azureResourceManager, String rgName, String serviceName,
                                   String appName, String module, RuntimeVersion jdkVersion,
                                   File file, Integer cpu, Integer memory, Integer instanceCount) {
        SpringService service = azureResourceManager.springServices().getByResourceGroup(rgName, serviceName);
        service.apps().getByName(appName)
               .getActiveDeployment()
               .update()
               .withCpu(cpu)
               .withMemory(memory)
               .withInstance(instanceCount)
               .withRuntime(jdkVersion)
               .apply();
        log.info("--------Update deployment done.-----------");
        if (module == null) {
            log.info("Deploying source code with single module.");
            service.apps().define(appName)
                   .defineActiveDeployment(DEFAULT_DEPLOYMENT_NAME)
                   .withSourceCodeTarGzFile(file)
                   .withSingleModule()
                   .withRuntime(jdkVersion)
                   .attach()
                   .create();
        } else {
            log.info("Deploying source code with target module {}.", module);
            service.apps().define(appName)
                   .defineActiveDeployment(DEFAULT_DEPLOYMENT_NAME)
                   .withSourceCodeTarGzFile(file)
                   .withTargetModule(module)
                   .withRuntime(jdkVersion)
                   .attach()
                   .create();
        }
        log.info("Successfully deploy source code.");
    }

    private RuntimeVersion getJavaVersion(String javaVersion) {
        RuntimeVersion runtimeVersion = new RuntimeVersion();
        switch (javaVersion) {
            case "17" -> runtimeVersion = RuntimeVersion.JAVA_17;
            case "11" -> runtimeVersion = RuntimeVersion.JAVA_11;
            case "8" -> runtimeVersion = RuntimeVersion.JAVA_8;
        }
        return runtimeVersion;
    }

    private AzureResourceManager getAzureResourceManager(OAuth2AuthorizedClient management, String subscriptionId) {
        if (subscriptionId == null) {
            return ResourceManagerUtils.getResourceManager(management.getAccessToken().getTokenValue());
        } else {
            return ResourceManagerUtils.getResourceManager(management.getAccessToken().getTokenValue(), subscriptionId);
        }
    }

}
