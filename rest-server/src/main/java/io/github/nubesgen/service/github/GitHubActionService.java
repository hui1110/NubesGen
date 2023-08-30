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
import io.github.nubesgen.utils.AzureResourceManagerUtils;
import io.github.nubesgen.utils.GithubUtils;
import okhttp3.Request;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
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

import static io.github.nubesgen.service.azure.springapps.Constants.Tier.StandardGen2;

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

    public GitHubActionService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Check whether the workflow file exists.
     *
     * @param url the repository url
     * @param branchName the branch name
     * @return true if the workflow file exists, otherwise false
     */
    public boolean checkWorkFlowFile(String url, String branchName, String tier){
        String pathName = GithubUtils.downloadSourceCodeFromGitHub(url, branchName);
        String workflowFileName = tier.equals(StandardGen2) ? WORKFLOW_jar_FILE_NAME : WORKFLOW_SOURCE_CODE_FILE_NAME;
        File file = new File(String.format("%s/.github/workflows/%s", pathName, workflowFileName));
        boolean exists = file.exists();
        GithubUtils.deleteRepositoryDirectory(new File(pathName));
        return exists;
    }

    /**
     * Create credential.
     *
     * @param management The OAuth2AuthorizedClient.
     * @param subscriptionId The subscription id.
     * @param appName The app name.
     * @param url The repository url.
     * @param branchName The branch name.
     * @return The client id.
     */
    public String createCredentials(OAuth2AuthorizedClient management, String subscriptionId, String appName, String url, String branchName) {
        branchName = Objects.equals(branchName, "null") ? "main" : branchName;
        Map<String, String> credentialMap = createServicePrincipal(management, subscriptionId, appName);
        log.info("Service principal created successfully");

        assignedRoleToServicePrincipal(management, subscriptionId, credentialMap.get("principalId"));
        log.info("Role assigned to service principal successfully");

        String tenantId = AzureResourceManagerUtils.getTenantId(management, subscriptionId);
        String username = GithubUtils.getUserName(url);
        String pathName = GithubUtils.getPathName(url);
        createFederatedCredential(management, appName, tenantId, credentialMap.get("objectId"), username, pathName, branchName);
        log.info("Federated credential created successfully");
        return credentialMap.get("clientId");
    }

    /**
     * Create service principal.
     *
     * @param management The OAuth2AuthorizedClient.
     * @param subscriptionId the subscription id.
     * @param appName The app name.
     * @return The map of service principal credential.
     */
    private Map<String, String> createServicePrincipal(OAuth2AuthorizedClient management, String subscriptionId, String appName) {
        String tenantId = AzureResourceManagerUtils.getTenantId(management, subscriptionId);
        appName = "asa-button-".concat(appName);
        Map<String, String> map = new HashMap<>();
        GraphServiceClient<Request> graphClient = getGraphClient(management, tenantId);
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
     *
     * @param management The OAuth2AuthorizedClient.
     * @param subscriptionId The subscription id.
     * @param principalId The principal id.
     */
    private void assignedRoleToServicePrincipal(OAuth2AuthorizedClient management, String subscriptionId, String principalId) {
        final AzureResourceManager azureResourceManager = AzureResourceManagerUtils.getAzureResourceManager(management, subscriptionId);
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
     *
     * @param management The OAuth2AuthorizedClient.
     * @param appName The app name.
     * @param tenantId The tenant id.
     * @param objectId The object id.
     * @param username The username.
     * @param pathName The path name.
     * @param branchName The branch name.
     */
    private void createFederatedCredential(OAuth2AuthorizedClient management, String appName, String tenantId, String objectId, String username, String pathName, String branchName) {
        GraphServiceClient<Request> graphClient = getGraphClient(management, tenantId);
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
     *
     * @param management The OAuth2AuthorizedClient.
     * @param tenantId The tenant id.
     * @return The graph client.
     */
    private GraphServiceClient<Request> getGraphClient(OAuth2AuthorizedClient management, String tenantId) {
        final List<String> scopes = List.of(GRAPH_SCOPE);
        final ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                .clientId(management.getClientRegistration().getClientId()).tenantId(tenantId).clientSecret(management.getClientRegistration().getClientSecret()).build();
        final TokenCredentialAuthProvider authProvider = new TokenCredentialAuthProvider(
                scopes, clientSecretCredential);
        return GraphServiceClient.builder().authenticationProvider(authProvider).buildClient();
    }

    /**
     * Push secrets to GitHub.
     *
     * @param management The OAuth2AuthorizedClient.
     * @param subscriptionId The subscription id.
     * @param serviceName The service name.
     * @param appName The app name.
     * @param clientId The client id.
     * @throws SodiumException The SodiumException.
     */
    public String pushSecretsToGitHub(OAuth2AuthorizedClient management, String subscriptionId, String resourceGroupName, String serviceName, String appName, String url, String clientId, String code, String tier) throws SodiumException {
        String tenantId = AzureResourceManagerUtils.getTenantId(management, subscriptionId);

        Map<String, String> map = new HashMap<>();
        map.put("AZURE_SPRING_SERVICE_NAME", serviceName);
        map.put("AZURE_SPRING_APP_NAME", appName);
        map.put("AZURE_CLIENT_ID", clientId);
        map.put("AZURE_TENANT_ID", tenantId);
        map.put("AZURE_SUBSCRIPTION_ID", subscriptionId);
        if(tier.equals(StandardGen2)){
            map.put("AZURE_RESOURCE_GROUP", resourceGroupName);
        }

        String username = GithubUtils.getUserName(url);
        String pathName = GithubUtils.getPathName(url);
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
     * Get the access token from GitHub.
     *
     * @param authorizationCode The authorization code from GitHub.
     * @return The access token.
     */
    private String getAccessToken(String authorizationCode) {
        GitHubTokenResult result;
        try {
            Map<String, String> map = new HashMap<>();
            map.put("client_id", "27c83ffc0f8fd2a9859b");
            map.put("client_secret", "33761dd44924c97ee87a90aa238c12a5f0e20a29");
            map.put("redirect_uri", "http://localhost:8080/asa-github-code.html");
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
     *
     * @param username The username.
     * @param pathName The path name.
     * @param accessToken The access token.
     * @return The public key.
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
     * Push workflow file.
     *
     * @param url repository url
     * @param branchName branch name
     * @param accessToken access token
     */
    public void generateWorkflowFile(String url, String branchName, String module, String javaVersion, String accessToken, String tier) {
        try {
            String pathName = GithubUtils.downloadSourceCodeFromGitHub(url, branchName);
            branchName = Objects.equals(branchName, "null") ? "main" : branchName;
            File path = new File(pathName);
            Path project = path.toPath();
            String workflowFileName = tier.equals(StandardGen2) ? WORKFLOW_jar_FILE_NAME : WORKFLOW_SOURCE_CODE_FILE_NAME;
            Path output = project.resolve(String.format(".github/workflows/%s", workflowFileName));

            ClassPathResource resource = new ClassPathResource(String.format("workflows/%s", workflowFileName));
            InputStream inputStream = resource.getInputStream();
            String yaml;
            if (Objects.equals(tier, StandardGen2)) {
                String content = new String(FileCopyUtils.copyToByteArray(inputStream));
                String jarPath = getJarPath(url, branchName, module);
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
            } else {
                Files.delete(output);
            }

            Files.createFile(output);
            FileCopyUtils.copy(resource.getInputStream(), Files.newOutputStream(output, StandardOpenOption.APPEND));
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output))) {
                writer.write(yaml);
            }

            String username = GithubUtils.getUserName(url);
            new GitWrapper()
                    .gitInit(path, branchName)
                    .gitAdd()
                    .gitCommit(username, "SpringIntegSupport@microsoft.com", tier)
                    .gitPush(url, username, accessToken)
                    .gitClean();
            GithubUtils.deleteRepositoryDirectory(path);
        } catch (Exception e) {
            throw new RuntimeException("Error while pushing workflow file", e);
        }
        log.info("Workflow file pushed to GitHub successfully");
    }

    /**
     * Get project name and java version from pom.xml.
     *
     * @param url repository url
     * @param branchName branch name
     * @param module module name
     * @return project name and java version
     */
    private synchronized String getJarPath(String url, String branchName, String module) {
        String pathName = GithubUtils.downloadSourceCodeFromGitHub(url, branchName);
        assert pathName != null;
        Model model;
        if (module.equals("null")) {
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
            } catch (Exception e) {
                GithubUtils.deleteRepositoryDirectory(new File(pathName));
                throw new RuntimeException(e.getMessage());
            }
        }
        String artifactId = model.getArtifactId();
        String version = model.getVersion();
        return module.equals("null") ? String.format("target/%s-%s.jar", artifactId, version) : String.format("%s/target/%s-%s.jar", module, artifactId, version);
    }

    private String encryptSecretValue(String secretValue, String key) throws SodiumException {
        SodiumJava sodium = new SodiumJava();
        LazySodiumJava lazySodium = new LazySodiumJava(sodium, StandardCharsets.UTF_8);
        String ciphertext = lazySodium.cryptoBoxSealEasy(secretValue, Key.fromBase64String(key));
        return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(ciphertext));
    }

}
