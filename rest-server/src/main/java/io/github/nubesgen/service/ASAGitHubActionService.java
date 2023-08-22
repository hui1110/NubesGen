package io.github.nubesgen.service;

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
import io.github.nubesgen.model.GitHubActionRunStatus;
import io.github.nubesgen.model.GitHubRepositoryPublicKey;
import io.github.nubesgen.model.GitHubTokenResult;
import io.github.nubesgen.model.GitWrapper;
import io.github.nubesgen.utils.ASADeployUtils;
import okhttp3.Request;
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

@Service
public final class ASAGitHubActionService {

    private static final String BASE_URI = "https://github.com";
    private static final String API_BASE_URI = "https://api.github.com";
    private static final String ACCESS_TOKEN_PATH = BASE_URI + "/login/oauth/access_token";
    private static final String CREATE_REPO_SECRET_PATH = API_BASE_URI + "/repos/%s/%s/actions/secrets/%s";
    private static final String WORKFLOW_RUNS_PATH = API_BASE_URI + "/repos/%s/%s/actions/workflows/%s/%s?per_page=1";
    private static final String WORKFLOW_FILE_NAME = "deploy-source-code-to-asa.yml";
    private static final String GRAPH_SCOPE = "https://graph.microsoft.com/.default";
    private static final String FEDERATED_CREDENTIAL_ISSUE = "https://token.actions.githubusercontent.com";
    private final Logger log = LoggerFactory.getLogger(ASAGitHubActionService.class);

    private final WebClient webClient;

    public ASAGitHubActionService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Check whether the workflow file exists.
     *
     * @param url the repository url
     * @param branchName the branch name
     * @return true if the workflow file exists, otherwise false
     */
    public boolean checkWorkFlowFile(String url, String branchName){
        String pathName = ASADeployUtils.downloadSourceCodeFromGitHub(url, branchName);
        File file = new File(pathName+ "/.github/workflows/" + "deploy-source-code-to-asa.yml");
        return file.exists();
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
    public String credentialCreation(OAuth2AuthorizedClient management, String subscriptionId, String appName, String url, String branchName) {
        branchName = Objects.equals(branchName, "null") ? "main" : branchName;
        Map<String, String> credentialMap = createServicePrincipal(management, subscriptionId, appName);
        log.info("Service principal created successfully");

        assignedRoleToServicePrincipal(management, subscriptionId, credentialMap.get("principalId"));
        log.info("Role assigned to service principal successfully");

        String tenantId = ASADeployUtils.getTenantId(management, subscriptionId);
        String username = ASADeployUtils.getUserName(url);
        String pathName = ASADeployUtils.getPathName(url);
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
        String tenantId = ASADeployUtils.getTenantId(management, subscriptionId);
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
        final AzureResourceManager azureResourceManager = ASADeployUtils.getAzureResourceManager(management, subscriptionId);
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
    public String pushSecretsToGitHub(OAuth2AuthorizedClient management, OAuth2AuthorizedClient github, String subscriptionId, String serviceName, String appName, String url, String clientId, String code) throws SodiumException {
        String tenantId = ASADeployUtils.getTenantId(management, subscriptionId);

        Map<String, String> map = new HashMap<>();
        map.put("AZURE_SPRING_SERVICE_NAME", serviceName);
        map.put("AZURE_SPRING_APP_NAME", appName);
        map.put("AZURE_CLIENT_ID", clientId);
        map.put("AZURE_TENANT_ID", tenantId);
        map.put("AZURE_SUBSCRIPTION_ID", subscriptionId);

        String username = ASADeployUtils.getUserName(url);
        String pathName = ASADeployUtils.getPathName(url);
//        String accessToken = getAccessToken(code);
        String accessToken = github.getAccessToken().getTokenValue();
        System.out.println("accessToken: " + accessToken);
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
    public void generateWorkflowFile(String url, String branchName, String module, String javaVersion, String accessToken) {
        try {
            branchName = Objects.equals(branchName, "null") ? "main" : branchName;
            String pathName = ASADeployUtils.getPathName(url);
            File path = new File(pathName);
            Path project = path.toPath();
            Path output = project.resolve(".github/workflows/" + WORKFLOW_FILE_NAME);

            ClassPathResource resource = new ClassPathResource("workflows/" + WORKFLOW_FILE_NAME);
            InputStream inputStream= resource.getInputStream();
            String content = new String(FileCopyUtils.copyToByteArray(inputStream));

            content = content.replace("${push-branches}", branchName).replace("${java-version}", javaVersion);
            if (!Objects.equals(module, "null")) {
                content = content.replace("${target-module}", module); // withTargetModule
            } else {
                content = content.replace("${target-module}", ""); // withoutTargetModule
            }

            if (!Files.exists(output)) {
                Files.createDirectories(output.getParent());
            } else {
                Files.delete(output);
            }

            Files.createFile(output);
            FileCopyUtils.copy(resource.getInputStream(), Files.newOutputStream(output, StandardOpenOption.APPEND));
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output))) {
                writer.write(content);
            }

            String username = ASADeployUtils.getUserName(url);
            new GitWrapper()
                    .gitInit(path, branchName)
                    .gitAdd()
                    .gitCommit(username, "SpringIntegSupport@microsoft.com")
                    .gitPush(url, username, accessToken)
                    .gitClean();
            ASADeployUtils.deleteRepositoryDirectory(path);
        } catch (Exception e) {
            throw new RuntimeException("Error while pushing workflow file", e);
        }
        log.info("Workflow file pushed to GitHub successfully");
    }

    /**
     * Get the GitHub action status.
     *
     * @param username the username
     * @param pathName the path name
     * @param accessToken the access token
     * @return the GitHub action status
     */
    public String getGitHubActionStatus(String username, String pathName, String accessToken) {
        GitHubActionRunStatus gitHubActionRunStatus = webClient
                .get()
                .uri(String.format(WORKFLOW_RUNS_PATH, username, pathName, WORKFLOW_FILE_NAME, "runs"))
                .header("Authorization", "token " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(GitHubActionRunStatus.class)
                .block();
        assert gitHubActionRunStatus != null;
        return gitHubActionRunStatus.getWorkflowRuns().get(0).getStatus();
    }

    private String encryptSecretValue(String secretValue, String key) throws SodiumException {
        SodiumJava sodium = new SodiumJava();
        LazySodiumJava lazySodium = new LazySodiumJava(sodium, StandardCharsets.UTF_8);
        String ciphertext = lazySodium.cryptoBoxSealEasy(secretValue, Key.fromBase64String(key));
        return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(ciphertext));
    }

}
