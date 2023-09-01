package io.github.nubesgen.service.github;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.authorization.models.BuiltInRole;
import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import com.goterl.lazysodium.exceptions.SodiumException;
import com.goterl.lazysodium.utils.Key;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.models.Application;
import com.microsoft.graph.models.FederatedIdentityCredential;
import com.microsoft.graph.models.ServicePrincipal;
import com.microsoft.graph.requests.GraphServiceClient;
import io.github.nubesgen.model.github.GitHubRepositoryPublicKey;
import io.github.nubesgen.model.github.GitHubTokenResult;
import io.github.nubesgen.model.github.GitWrapper;
import io.github.nubesgen.service.azure.springapps.DeploymentManager;
import io.github.nubesgen.utils.GithubUtils;
import okhttp3.Request;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.github.nubesgen.service.azure.springapps.Constants.SpringApps_email;
import static io.github.nubesgen.service.azure.springapps.Constants.StandardGen2;

/**
 * Providing operations for generate GitHub Actions work flow file.
 */
@Service
public final class GitHubActionService {

    private static final String BASE_URI = "https://github.com";
    private static final String API_BASE_URI = "https://api.github.com";
    private static final String ACCESS_TOKEN_PATH = BASE_URI + "/login/oauth/access_token";
    private static final String CREATE_REPO_SECRET_PATH = API_BASE_URI + "/repos/%s/%s/actions/secrets/%s";
    private static final String WORKFLOW_SOURCE_CODE_FILE_NAME = "deploy-source-code-to-asa.yml";
    private static final String WORKFLOW_jar_FILE_NAME = "deploy-jar-to-asa.yml";
    private static final String GRAPH_SCOPE = "https://graph.microsoft.com/.default";
    private static final String FEDERATED_CREDENTIAL_ISSUE = "https://token.actions.githubusercontent.com";
    private final Logger log = LoggerFactory.getLogger(GitHubActionService.class);
    private final WebClient webClient;

    @Value("${azure.github.client-id}")
    private String GITHUB_CLIENT_ID;
    @Value("${azure.github.client-secret}")
    private String GITHUB_CLIENT_SECRET;
    @Value("${azure.github.redirect-uri}")
    private String GITHUB_REDIRECT_URI;

    public GitHubActionService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Check whether the workflow file exists.
     *
     * @param url the repository url
     * @param branchName the branch name
     * @return the branch name
     */
    public synchronized Map<String, String> checkWorkFlowFile(String url, String branchName, String tier){
        Map<String, String> map = GithubUtils.downloadSourceCodeFromGitHub(url, branchName);
        String pathName = map.get("pathName");
        String workflowFileName = tier.equals(StandardGen2) ? WORKFLOW_jar_FILE_NAME : WORKFLOW_SOURCE_CODE_FILE_NAME;
        File file = new File(String.format("%s/.github/workflows/%s", pathName, workflowFileName));
        if(file.exists()){
            GithubUtils.deleteRepositoryDirectory(pathName);
            throw new RuntimeException("Workflow file already exists");
        } else {
            return map;
        }
    }

    /**
     * Create credential.
     *
     * @param deploymentManager the deployment manager.
     * @param appName the app name.
     * @param url the GitHub repository url.
     * @param branchName the branch name.
     * @param tenantId the tenant id.
     * @param clientId the client id.
     * @param clientSecret the client secret.
     * @param subscriptionId the subscription id.
     * @return the client id of the created Service Principal.
     */
    public String createCredentials(DeploymentManager deploymentManager, String appName, String url, String branchName, String tenantId, String clientId, String clientSecret, String subscriptionId) {
        Map<String, String> credentialMap = createServicePrincipal(appName, tenantId, clientId, clientSecret);
        log.info("Service principal created successfully");

        assignedRoleToServicePrincipal(deploymentManager.getAzureResourceManager(), subscriptionId,credentialMap.get("principalId"));
        log.info("Role assigned to service principal successfully");

        String username = GithubUtils.getGitHubUserName(url);
        String pathName = GithubUtils.getGitHubRepositoryName(url);
        createFederatedCredential(appName, tenantId, clientId, clientSecret, credentialMap.get("objectId"), username, pathName, branchName);
        log.info("Federated credential created successfully");
        return credentialMap.get("clientId");
    }

    /**
     * Push secrets to GitHub.
     *
     * @param tenantId the tenant id.
     * @param subscriptionId the subscription id.
     * @param serviceName the service name.
     * @param appName the app name.
     * @param clientId the client id.
     * @throws SodiumException encrypted secret failed.
     */
    public String pushSecretsToGitHub(String tenantId, String subscriptionId, String resourceGroupName, String serviceName, String appName, String url, String clientId, String code, String tier) throws SodiumException {
        Map<String, String> map = new HashMap<>();
        map.put("AZURE_SPRING_SERVICE_NAME", serviceName);
        map.put("AZURE_SPRING_APP_NAME", appName);
        map.put("AZURE_CLIENT_ID", clientId);
        map.put("AZURE_TENANT_ID", tenantId);
        map.put("AZURE_SUBSCRIPTION_ID", subscriptionId);
        if(tier.equals(StandardGen2)){
            map.put("AZURE_RESOURCE_GROUP", resourceGroupName);
        }

        String username = GithubUtils.getGitHubUserName(url);
        String pathName = GithubUtils.getGitHubRepositoryName(url);
        String accessToken = getAccessToken(code);
        GitHubRepositoryPublicKey repositoryPublicKey = getGitHubRepositoryPublicKey(username, pathName, accessToken);
        Map<String, String> secretMap = new HashMap<>();
        secretMap.put("key_id", repositoryPublicKey.getKeyId());

        for (Map.Entry<String, String> entry : map.entrySet()) {
            secretMap.put("encrypted_value", encryptSecretValue(entry.getValue(), repositoryPublicKey.getKey()));
            try {
                webClient
                        .put()
                        .uri(String.format(CREATE_REPO_SECRET_PATH, username, pathName, entry.getKey()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(secretMap))
                        .header("Authorization", "token " + accessToken)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve().toBodilessEntity().block();
            } catch (Exception e) {
                throw new RuntimeException("Error while pushing secrets to GitHub", e);
            }
        }
        log.info("Secrets pushed to GitHub successfully");
        return accessToken;
    }

    /**
     * Generate workflow file and push.
     *
     * @param url the GitHub repository url.
     * @param branchName the branch name.
     * @param accessToken the access token.
     */
    public synchronized void generateWorkflowFile(String pathName, String url, String branchName, String module, String javaVersion, String accessToken, String tier) {
        try {
            File path = new File(pathName);
            Path project = path.toPath();
            String workflowFileName = tier.equals(StandardGen2) ? WORKFLOW_jar_FILE_NAME : WORKFLOW_SOURCE_CODE_FILE_NAME;
            Path output = project.resolve(String.format(".github/workflows/%s", workflowFileName));

            ClassPathResource resource = new ClassPathResource(String.format("workflows/%s", workflowFileName));
            InputStream inputStream = resource.getInputStream();
            String yaml;
            if (Objects.equals(tier, StandardGen2)) {
                String content = new String(FileCopyUtils.copyToByteArray(inputStream));
                String jarPath = getJarPath(pathName, module);
                yaml = content.replace("${build-version}", javaVersion.substring(javaVersion.lastIndexOf("_") + 1));
                yaml = yaml.replace("${jar-path}", jarPath).replace("${java-version}", javaVersion);
            } else if(Objects.equals(tier, "Standard")) {
                StringBuilder content = new StringBuilder(new String(FileCopyUtils.copyToByteArray(inputStream)));
                content.append("\n").append("          ").append("runtime-version: ").append(javaVersion);// withRuntimeVersion
                if (!Objects.equals(module, "null")) {
                    content.append("\n").append("          ").append("target-module: ").append(module);// withTargetModule
                }
                yaml = content.toString();
            } else {
                StringBuilder content = new StringBuilder(new String(FileCopyUtils.copyToByteArray(inputStream)));
                content.append("\n").append("          ").append("builder: ").append("default");// withBuilder
                content.append("\n").append("          ").append("build-env: ").append("-BP_JVM_VERSION ").append(javaVersion.substring(javaVersion.lastIndexOf("_") + 1));// withBuildEnv
                if (!Objects.equals(module, "null")) {
                    content.append(" -BP_MAVEN_BUILT_MODULE ").append(module);// withTargetModule
                }
                yaml = content.toString();
            }
            yaml = yaml.replace("${push-branches}", branchName);
            yaml = yaml.concat("\n");
            if (!Files.exists(output)) {
                Files.createDirectories(output.getParent());
            }
            Files.createFile(output);
            FileCopyUtils.copy(resource.getInputStream(), Files.newOutputStream(output, StandardOpenOption.APPEND));
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output))) {
                writer.write(yaml);
            }
            pushWorkFlowFile(path, url, branchName, accessToken, tier);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            GithubUtils.deleteRepositoryDirectory(pathName);
        }
        log.info("Workflow file generated successfully.");
    }

    /**
     * Create service principal.
     */
    private Map<String, String> createServicePrincipal(String appName, String tenantId, String clientId, String clientSecret) {
        appName = "asa-button-".concat(appName);
        Map<String, String> map = new HashMap<>();
        GraphServiceClient<Request> graphClient = getGraphClient(tenantId,clientId, clientSecret);
        try {
            Application application = new Application();
            application.displayName = appName;
            Application applicationResult = graphClient.applications()
                    .buildRequest()
                    .post(application);

            ServicePrincipal servicePrincipal = new ServicePrincipal();
            servicePrincipal.appId = applicationResult.appId;
            ServicePrincipal servicePrincipalResult = graphClient.servicePrincipals()
                    .buildRequest()
                    .post(servicePrincipal);

            map.put("objectId", applicationResult.id);
            map.put("clientId", applicationResult.appId);
            map.put("principalId", servicePrincipalResult.id);
        } catch (Exception e) {
            throw new RuntimeException("Error while creating service principal", e);
        }
        return map;
    }

    /**
     * Assigned role to service principal.
     */
    private void assignedRoleToServicePrincipal(AzureResourceManager azureResourceManager, String subscriptionId, String principalId) {
        try {
            azureResourceManager
                    .accessManagement()
                    .roleAssignments()
                    .define(UUID.randomUUID().toString())
                    .forObjectId(principalId)
                    .withBuiltInRole(BuiltInRole.CONTRIBUTOR)
                    .withSubscriptionScope(subscriptionId)
                    .withDescription("contributor role")
                    .create();
        } catch (Exception e) {
            throw new RuntimeException("Error while assigning role to service principal", e);
        }
    }

    /**
     * Create federated credential for service principal.
     */
    private void createFederatedCredential(String appName, String tenantId, String clientId, String secret, String objectId, String username, String pathName, String branchName) {
        GraphServiceClient<Request> graphClient = getGraphClient(tenantId, clientId, secret);
        FederatedIdentityCredential federatedIdentityCredential = new FederatedIdentityCredential();
        federatedIdentityCredential.name = appName;
        federatedIdentityCredential.issuer = FEDERATED_CREDENTIAL_ISSUE;
        federatedIdentityCredential.subject = String.format("repo:%s/%s:ref:refs/heads/%s", username, pathName, branchName);
        LinkedList<String> audiencesList = new LinkedList<>();
        audiencesList.add("api://AzureADTokenExchange");
        federatedIdentityCredential.audiences = audiencesList;
        try {
            graphClient.applications(objectId).federatedIdentityCredentials()
                    .buildRequest()
                    .post(federatedIdentityCredential);
        } catch (Exception e) {
            throw new RuntimeException("Error while creating federated credential", e);
        }
    }

    /**
     * Get the graph client.
     */
    private GraphServiceClient<Request> getGraphClient(String tenantId, String clientId, String clientSecret) {
        final List<String> scopes = List.of(GRAPH_SCOPE);
        final ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                .clientId(clientId).tenantId(tenantId).clientSecret(clientSecret).build();
        final TokenCredentialAuthProvider authProvider = new TokenCredentialAuthProvider(
                scopes, clientSecretCredential);
        return GraphServiceClient.builder().authenticationProvider(authProvider).buildClient();
    }

    /**
     * Get the access token from GitHub.
     */
    private String getAccessToken(String authorizationCode) {
        GitHubTokenResult result;
        try {
            Map<String, String> map = new HashMap<>();
            map.put("client_id", GITHUB_CLIENT_ID);
            map.put("client_secret", GITHUB_CLIENT_SECRET);
            map.put("redirect_uri", GITHUB_REDIRECT_URI);
            map.put("code", authorizationCode);
            result = webClient.post()
                    .uri(ACCESS_TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(map))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(GitHubTokenResult.class)
                    .block();
        } catch (RuntimeException ex) {
            throw new RuntimeException("Error while authenticating", ex);
        }
        if (result == null || result.getAccessToken() == null) {
            throw new RuntimeException("Error while authenticating");
        }
        return result.getAccessToken();
    }

    /**
     * Get the public key from GitHub.
     */
    private GitHubRepositoryPublicKey getGitHubRepositoryPublicKey(String username, String pathName, String accessToken) {
        return webClient
                .get()
                .uri(String.format(CREATE_REPO_SECRET_PATH, username, pathName, "public-key"))
                .header("Authorization", "token " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(GitHubRepositoryPublicKey.class)
                .block();
    }

    /**
     * Get Azure CLI deploy Jar path.
     */
    private synchronized String getJarPath(String pathName, String module) {
        Model model;
        try {
            if (module.equals("null")) {
                try (FileInputStream fis = new FileInputStream(pathName.concat("/pom.xml"))) {
                    MavenXpp3Reader reader = new MavenXpp3Reader();
                    model = reader.read(fis);
                }
            } else {
                try (FileInputStream fis = new FileInputStream(pathName.concat("/").concat(module.concat("/pom.xml")))) {
                    MavenXpp3Reader reader = new MavenXpp3Reader();
                    model = reader.read(fis);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        String artifactId = model.getArtifactId();
        String version = model.getVersion();
        return module.equals("null") ? String.format("target/%s-%s.jar", artifactId, version) : String.format("%s/target/%s-%s.jar", module, artifactId, version);
    }

    /**
     * Push workflow file.
     */
    private void pushWorkFlowFile(File file, String url, String branchName, String accessToken, String tier){
        try {
            String username = GithubUtils.getGitHubUserName(url);
            new GitWrapper()
                    .gitInit(file, branchName)
                    .gitAdd()
                    .gitCommit(username, SpringApps_email, tier)
                    .gitPush(url, username, accessToken)
                    .gitClean();
        }catch (Exception e) {
            throw new RuntimeException("Error while pushing workflow file", e);
        }
        log.info("Workflow file pushed to GitHub successfully");
    }

    /**
     * Encrypt the secret value.
     */
    private String encryptSecretValue(String secretValue, String key) throws SodiumException {
        SodiumJava sodium = new SodiumJava();
        LazySodiumJava lazySodium = new LazySodiumJava(sodium, StandardCharsets.UTF_8);
        String ciphertext = lazySodium.cryptoBoxSealEasy(secretValue, Key.fromBase64String(key));
        return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(ciphertext));
    }

}
