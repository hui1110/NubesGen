package io.github.nubesgen.service;

import com.azure.core.management.Region;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appplatform.models.RuntimeVersion;
import com.azure.resourcemanager.appplatform.models.SpringApp;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import com.azure.resourcemanager.resources.models.Subscription;
import io.github.nubesgen.model.ASAInstance;
import io.github.nubesgen.model.ProjectInstance;
import io.github.nubesgen.model.RegionInstance;
import io.github.nubesgen.model.ResourceGrooupInstance;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

@Service
public class AzureDeployService {

    private final Logger log = LoggerFactory.getLogger(AzureDeployService.class);
    private static final String DEFAULT_DEPLOYMENT_NAME = "default";

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
     * Get Azure Spring Apps instance list.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @return Azure Spring Apps instance list
     */
    public List<ASAInstance> getServiceinstanceList(OAuth2AuthorizedClient management, String subscriptionId,
                                                    String resourceGroupName) {
        AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
        return azureResourceManager.springServices().list().stream().filter(springService -> Objects.equals(springService.resourceGroupName(), resourceGroupName)).sorted(Comparator.comparing(SpringService::name))
                                   .map(springService -> new ASAInstance(springService.region(),
                                       springService.resourceGroupName(), springService.id(), springService.name(),
                                       springService.sku().tier()))
                                   .collect(Collectors.toList());
    }

    /**
     * Get region list.
     *
     * @return the region instance list
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
    public ProjectInstance getNameAndJavaVersion(String url, String branchName, String module) {
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
                throw new RuntimeException(e);
            }
        } else {
            try (FileInputStream fis = new FileInputStream(pathName.concat("/").concat(module.concat("/pom.xml")))) {
                MavenXpp3Reader reader = new MavenXpp3Reader();
                model = reader.read(fis);
                if (model.getProperties().isEmpty()) {
                    FileInputStream fisParent = new FileInputStream(pathName.concat("/pom.xml"));
                    MavenXpp3Reader readerParent = new MavenXpp3Reader();
                    Properties properties = readerParent.read(fisParent).getProperties();
                    fisParent.close();
                    Assert.isTrue(properties.isEmpty() || properties.containsKey("java.version"), "Please configure the java version in the pom.xml file of module a or parent.");
                    model.getProperties().put("java.version", properties.getProperty("java.version"));
                }
            } catch (Exception e) {
                e.printStackTrace();
                deleteRepositoryDirectory(new File(pathName));
                throw new RuntimeException(e);
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
     * Update Azure Spring Apps deployment.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     */
    public void updateActiveDeployment(OAuth2AuthorizedClient management, String subscriptionId,
                                       String resourceGroupName,
                                       String serviceName, String appName) {
        try {
            log.info("Updating deployment in app {} ", appName);
            AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
            SpringService service = azureResourceManager.springServices().getByResourceGroup(resourceGroupName,
                serviceName);
            SpringApp app = service.apps()
                                   .getByName(appName)
                                   .update()
                                   .withActiveDeployment(DEFAULT_DEPLOYMENT_NAME)
                                   .withDefaultPublicEndpoint()
                                   .withHttpsOnly()
                                   .apply();
            ResourceManagerUtils.print(app);
            log.info("Successfully update deployment in app {} ", appName);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Deploy source code to Azure Spring App instance.
     *
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param regionName the region name
     * @param resourceGroupName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param javaVersion the java version
     * @param url the repository url
     * @param branchName the branch name
     */
    public void deploySourceCodeToSpringApps(OAuth2AuthorizedClient management, String subscriptionId,
                                                          String regionName, String resourceGroupName,
                                                          String serviceName,
                                                          String appName,
                                                          String module, String javaVersion, Integer cpu,
                                                          Integer memory,
                                                          Integer instanceCount, String url, String branchName) throws IOException {
        module = Objects.equals(module, "null") ? null : module;
        String pathName = getRepositoryPath(url, branchName);
        Region region = Region.fromName(regionName);
        RuntimeVersion runtimeVersion = getJavaVersion(javaVersion);
        File sourceCode = createTarGzFile(new File(pathName));
        AzureResourceManager azureResourceManager = getAzureResourceManager(management, subscriptionId);
        try {
            if (!azureResourceManager.resourceGroups().contain(resourceGroupName)) {
                createResourceGroup(azureResourceManager, resourceGroupName, region);
            } else {
                log.info("Resource Group " + resourceGroupName + " already exists.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        try {
            if (azureResourceManager.springServices().checkNameAvailability(serviceName, region).nameAvailable()) {
                createASA(azureResourceManager, resourceGroupName, serviceName, region);
            } else {
                log.info("Azure Spring Apps " + serviceName + " already exists.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        try {
            deployToSpringApp(azureResourceManager, resourceGroupName, serviceName, appName, module, runtimeVersion,
                sourceCode,
                cpu, memory, instanceCount);
        } catch (Exception e) {
            e.printStackTrace();
            SpringService service = azureResourceManager.springServices().getByResourceGroup(resourceGroupName,
                serviceName);
            service.apps().deleteByName(appName);
            log.info("Delete app " + appName);
            throw new RuntimeException(e);
        }
    }

    private String getRepositoryPath(String url, String branchName) {
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

    private RuntimeVersion getJavaVersion(String javaVersion) {
        RuntimeVersion runtimeVersion = new RuntimeVersion();
        switch (javaVersion) {
            case "17" -> runtimeVersion = RuntimeVersion.JAVA_17;
            case "11" -> runtimeVersion = RuntimeVersion.JAVA_11;
            case "8" -> runtimeVersion = RuntimeVersion.JAVA_8;
        }
        return runtimeVersion;
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

    private void deleteRepositoryDirectory(File directory) {
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
        if (module == null) {
            log.info("Creating app {} and deploying source code with single module.", appName);
            service.apps().define(appName)
                   .defineActiveDeployment(DEFAULT_DEPLOYMENT_NAME)
                   .withSourceCodeTarGzFile(file)
                   .withSingleModule()
                   .withCpu(cpu)
                   .withMemory(memory)
                   .withInstance(instanceCount)
                   .withRuntime(jdkVersion)
                   .attach()
                   .create();
        } else {
            log.info("Creating app {} and deploying source code with target module {}.", appName, module);
            service.apps().define(appName)
                   .defineActiveDeployment(DEFAULT_DEPLOYMENT_NAME)
                   .withSourceCodeTarGzFile(file)
                   .withTargetModule(module)
                   .withCpu(cpu)
                   .withMemory(memory)
                   .withInstance(instanceCount)
                   .withRuntime(jdkVersion)
                   .attach()
                   .create();
        }
        log.info("Successfully create app {} and upload source code.", appName);
    }

    private AzureResourceManager getAzureResourceManager(OAuth2AuthorizedClient management, String subscriptionId) {
        if (subscriptionId == null) {
            return ResourceManagerUtils.getResourceManager(management.getAccessToken().getTokenValue());
        } else {
            return ResourceManagerUtils.getResourceManager(management.getAccessToken().getTokenValue(), subscriptionId);
        }
    }

}
