package io.github.nubesgen.service;

import com.azure.core.management.Region;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appplatform.models.RuntimeVersion;
import com.azure.resourcemanager.appplatform.models.SpringApp;
import com.azure.resourcemanager.appplatform.models.SpringApps;
import com.azure.resourcemanager.appplatform.models.SpringService;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import com.azure.resourcemanager.resources.models.Subscription;
import io.github.nubesgen.model.ASAInstance;
import io.github.nubesgen.model.AppInstance;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

@Service
public class AzureDeployService {

    private final Logger log = LoggerFactory.getLogger(AzureDeployService.class);
    private static final String DEFAULT_DEPLOYMENT_NAME = "default";

    /**
     * Get subscription list.
     * @param management OAuth2 authorization client after login
     * @return the subscription list
     */
    public List<SubscriptionInstance> subscriptionList(OAuth2AuthorizedClient management) {
        AzureResourceManager arm = ResourceManagerUtils.getResourceManager(management.getAccessToken().getTokenValue());
        return arm.subscriptions().list().stream().sorted(Comparator.comparing(Subscription::displayName))
                  .map(subscription -> new SubscriptionInstance(subscription.subscriptionId(), subscription.displayName()))
                  .collect(Collectors.toList());
    }

    /**
     * Get Azure Spring Apps instance list by subscription id and resource group name.
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param rgName the resource group name
     * @return Azure Spring Apps instance list
     */
    public List<ASAInstance> instanceList(OAuth2AuthorizedClient management, String subscriptionId, String rgName) {
        AzureResourceManager arm = ResourceManagerUtils.getResourceManager(management.getAccessToken().getTokenValue(), subscriptionId);
        return arm.springServices().list().stream().filter(springService -> Objects.equals(springService.resourceGroupName(), rgName)).sorted(Comparator.comparing(SpringService::name))
                  .map(springService -> new ASAInstance(springService.region(), springService.resourceGroupName(), springService.id(), springService.name(), springService.sku().tier()))
                  .collect(Collectors.toList());
    }

    /**
     * Deploy source code to Azure Spring App instance.
     * @param management OAuth2 authorization client after login
     * @param subscriptionId the subscription id
     * @param region the region
     * @param rgName the resource group name
     * @param serviceName the service name
     * @param appName the app name
     * @param jdkVersion the JDK version
     * @param pathName the source code path
     */
    public String deploySourceCodeToSpringApps(OAuth2AuthorizedClient management,String subscriptionId, Region region, String rgName, String serviceName, String appName, RuntimeVersion jdkVersion, String pathName, Integer cpu, Integer memory, Integer instanceCount) throws IOException {
        String res = "done";
        File file = new File(pathName);
        File sourceCode = createTarGzFile(file);
        deleteRepositoryDirectory(file);
        AzureResourceManager azureResourceManager = ResourceManagerUtils.getResourceManager(management.getAccessToken().getTokenValue(), subscriptionId);

        try {
            if (!azureResourceManager.resourceGroups().contain(rgName)) {
                createResourceGroup(azureResourceManager, rgName, region);
            } else {
                log.info("Resource Group " + rgName + " already exists.");
            }
        } catch (Exception e) {
            res = e.getMessage();
            e.printStackTrace();
//            azureResourceManager.resourceGroups().beginDeleteByName(rgName);
//            log.info("Delete Resource Group: " + rgName);
        }

        try {
            if (azureResourceManager.springServices().checkNameAvailability(serviceName, region).nameAvailable()) {
                createASA(azureResourceManager, rgName, serviceName, region);
            } else {
                log.info("Azure Spring Apps " + serviceName + " already exists.");
            }
        } catch (Exception e) {
            res = e.getMessage();
            e.printStackTrace();
//            azureResourceManager.resourceGroups().beginDeleteByName(rgName);
//            log.info("Delete Resource Group: " + rgName);
//            String id = azureResourceManager.springServices().getByResourceGroup(rgName, serviceName).id();
//            azureResourceManager.springServices().deleteById(id);
//            log.info("Delete Azure Spring Apps: " + serviceName);
        }

        try {
            deploySpringApp(azureResourceManager, rgName, serviceName, appName, jdkVersion, sourceCode, cpu, memory, instanceCount);
        } catch (Exception e) {
            res = e.getMessage();
//            e.printStackTrace();
//            azureResourceManager.resourceGroups().beginDeleteByName(rgName);
//            log.info("Delete Resource Group: " + rgName);
//            String id = azureResourceManager.springServices().getByResourceGroup(rgName, serviceName).id();
//            azureResourceManager.springServices().deleteById(id);
//            log.info("Delete Azure Spring Apps [" + serviceName + "] and app [" + appName + "]");
            SpringService service = azureResourceManager.springServices().getByResourceGroup(rgName, serviceName);
            service.apps().deleteByName(appName);
            log.info("Delete app " + appName);
        }
        return res;
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
            throw new RuntimeException(e);
        }
        return compressFile;
    }

    private void deleteRepositoryDirectory(File directory) {
        File tempGitDirectory;
        try {
            tempGitDirectory = new File(directory.toString());
            if(tempGitDirectory.exists()){
                FileUtils.deleteDirectory(tempGitDirectory);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createResourceGroup(AzureResourceManager azureResourceManager, String rgName, Region region) {
        log.info("Creating resource group: {}", rgName);
        azureResourceManager.resourceGroups().define(rgName)
                            .withRegion(region)
                            .create();
        log.info("Created resource group: {}", rgName);
    }

    private void createASA(AzureResourceManager azureResourceManager, String rgName, String serviceName, Region region) {
        log.info("Creating Azure Spring Apps {} in resource group {}", serviceName, rgName);
        SpringService service = azureResourceManager.springServices().define(serviceName)
                                                    .withRegion(region)
                                                    .withExistingResourceGroup(rgName)
                                                    .create();
        log.info("Created Azure Spring Apps: {} ", service.name());
        ResourceManagerUtils.print(service);
    }

    private void deploySpringApp(AzureResourceManager azureResourceManager, String rgName, String serviceName, String appName, RuntimeVersion jdkVersion,
                                 File file, Integer cpu, Integer memory, Integer instanceCount) {
        log.info("Creating app: {} and deploying source code...", appName);
        SpringService service = azureResourceManager.springServices().getByResourceGroup(rgName, serviceName);
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

        log.info("Successfully create app {} and upload source code.", appName);
    }

    public boolean updateActiveDeployment(OAuth2AuthorizedClient management, String subscriptionId, String rgName, String serviceName, String appName) {
        try {
            log.info("Updating deployment in app: {} ", appName);
            AzureResourceManager azureResourceManager = ResourceManagerUtils.getResourceManager(management.getAccessToken().getTokenValue(), subscriptionId);
            SpringService service = azureResourceManager.springServices().getByResourceGroup(rgName, serviceName);
            SpringApp app = service.apps()
                                   .getByName(appName)
                                   .update()
                                   .withActiveDeployment(DEFAULT_DEPLOYMENT_NAME)
                                   .withDefaultPublicEndpoint()
                                   .withHttpsOnly()
                                   .apply();
            ResourceManagerUtils.print(app);
            log.info("Successfully update deployment in app: {} ", appName);
            return true;
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
    public ProjectInstance getNameAndJavaVersion(String url, String branchName){
        String pathName = getRepositoryPath(url, branchName);
        assert pathName != null;
        String pomPath = pathName + "/pom.xml";
        Model model;
        try (FileInputStream fis = new FileInputStream(pomPath)){
            MavenXpp3Reader reader = new MavenXpp3Reader();
            model = reader.read(fis);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
        deleteRepositoryDirectory(new File(pathName));
        ProjectInstance projectInstance = new ProjectInstance();
        if(model != null) {
            Assert.isTrue(model.getName() != null, "Project name is not configured in pom.xml");
            Assert.isTrue(model.getProperties().containsKey("java.version"), "Java version is not configured in pom.xml");
            projectInstance.setName(model.getName());
            String version = String.valueOf(model.getProperties().get("java.version"));
            projectInstance.setVersion(Objects.equals(version, "null") ? null: "Java_" + version);
        }
        return projectInstance;
    }

    public String getRepositoryPath(String url, String branchName){
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

    public RuntimeVersion getJavaVersion(String javaVersion) {
        RuntimeVersion runtimeVersion = new RuntimeVersion();
        switch (javaVersion) {
            case "17" -> runtimeVersion = RuntimeVersion.JAVA_17;
            case "11" -> runtimeVersion = RuntimeVersion.JAVA_11;
            case "8" -> runtimeVersion = RuntimeVersion.JAVA_8;
        }
        return runtimeVersion;
    }

    public List<AppInstance> appList(OAuth2AuthorizedClient management, String rgName, String serviceName,
                                     String subscriptionId) {
        AzureResourceManager azureResourceManager =
            ResourceManagerUtils.getResourceManager(management.getAccessToken().getTokenValue(), subscriptionId);
        SpringApps apps = azureResourceManager.springServices().getByResourceGroup(rgName, serviceName).apps();
        return apps.list().stream().sorted(Comparator.comparing(SpringApp::name))
                   .map(springApp -> new AppInstance(springApp.id(), springApp.name(), springApp.getActiveDeployment() == null ? "---" : String.valueOf(springApp.getActiveDeployment().runtimeVersion()),springApp.getActiveDeployment() == null ? 0: springApp.getActiveDeployment().cpu(), springApp.getActiveDeployment() == null ? 0: springApp.getActiveDeployment().memoryInGB(), springApp.getActiveDeployment() == null || springApp.getActiveDeployment().instances() == null ? 0: springApp.getActiveDeployment().instances().size()))
                   .toList();
    }

    public List<RegionInstance> RegionList() {
        List<Region> regionArrayList = new ArrayList<>(Region.values());
        List<RegionInstance> resList = new ArrayList<>();
        if (!regionArrayList.isEmpty()) {
            for (Region region : regionArrayList) {
                resList.add(new RegionInstance(region.name(), region.label()));
            }
        }
        return resList.stream().sorted(Comparator.comparing(RegionInstance::getName)).collect(Collectors.toList());
    }

    public List<ResourceGrooupInstance> ResourceGroup(OAuth2AuthorizedClient management, String subscriptionId) {
        AzureResourceManager azureResourceManager =
            ResourceManagerUtils.getResourceManager(management.getAccessToken().getTokenValue(), subscriptionId);
        return azureResourceManager.resourceGroups().list().stream().sorted(Comparator.comparing(ResourceGroup::name))
                                   .map(resourceGroup -> new ResourceGrooupInstance(resourceGroup.name()))
                                   .collect(Collectors.toList());
    }

}
