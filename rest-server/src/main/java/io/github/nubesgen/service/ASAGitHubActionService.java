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
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.IOException;
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
    private static final String WORKFLOW_PATH = API_BASE_URI + "/repos/%s/%s/actions/workflows/%s/%s";
    private static final String WORKFLOW_FILE_PATH = "deploy-source-code-to-asa.yml";
    private static final String GRAPH_SCOPE = "https://graph.microsoft.com/.default";
    private static final String FEDERATED_CREDENTIAL_ISSUE = "https://token.actions.githubusercontent.com";

    private final WebClient webClient;

    public ASAGitHubActionService(WebClient webClient) {
        this.webClient = webClient;
    }

    public String getAccessToken(String authorizationCode) {
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

    public String getUserName(String url) {
        int beginIndex = StringUtils.ordinalIndexOf(url, "/", 3);
        int endIndex = StringUtils.ordinalIndexOf(url, "/", 4);
        return url.substring(beginIndex + 1, endIndex);
    }

    public boolean createWorkflowFile(String pathName, String branchName, String javaVersion, String module) throws IOException {
        File path = new File(pathName);
        Path project = path.toPath();
        Path output = project.resolve(".github/workflows/" + WORKFLOW_FILE_PATH);

        ClassPathResource resource = new ClassPathResource("workflows/" + WORKFLOW_FILE_PATH);
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
            File tempFile = new File(WORKFLOW_FILE_PATH);
            FileUtils.writeStringToFile(tempFile, content, "UTF-8");
            if(FileUtils.contentEqualsIgnoreEOL(tempFile, output.toFile(), "UTF-8")){
                Files.delete(tempFile.toPath());
                return false;
            }
            Files.delete(output);
        }

        Files.createFile(output);
        FileCopyUtils.copy(resource.getInputStream(), Files.newOutputStream(output, StandardOpenOption.APPEND));
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output))) {
            writer.write(content);
        }
        return true;
    }

    public GitHubRepositoryPublicKey getGitHubRepositoryPublicKey(String username, String pathName, String accessToken) {
        return webClient
                .get()
                .uri(String.format(CREATE_REPO_SECRET_PATH, username, pathName, "public-key"))
                .header("Authorization", "token " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(GitHubRepositoryPublicKey.class)
                .block();
    }

    public void pushSecretsToGitHub(OAuth2AuthorizedClient management, String subscriptionId, String serviceName, String appName, String username, String pathName, String accessToken, GitHubRepositoryPublicKey repositoryPublicKey, String clientId) throws SodiumException {
        String tenantId = ASADeployUtils.getTenantId(management, subscriptionId);
        Map<String, String> map = new HashMap<>();
        map.put("AZURE_SPRING_SERVICE_NAME", serviceName);
        map.put("AZURE_SPRING_APP_NAME", appName);
        map.put("AZURE_CLIENT_ID", clientId);
        map.put("AZURE_TENANT_ID", tenantId);
        map.put("AZURE_SUBSCRIPTION_ID", subscriptionId);

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
    }

    public Map<String, String> createdServicePrincipal(OAuth2AuthorizedClient management, String tenantId, String subscriptionId, String appName) {
        AzureResourceManager servicePrincipalOperationARM = ASADeployUtils.getServicePrincipalOperationARM(management, subscriptionId);
        appName = "asa-button-".concat(appName);
        Map<String, String> map = new HashMap<>();
        try {
            if (!Objects.isNull(servicePrincipalOperationARM.accessManagement().servicePrincipals().getByName(appName))) {
                return map;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    public void assignedRoleToServicePrincipal(OAuth2AuthorizedClient management, String subscriptionId, String principalId) {
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

    public void pushWorkflowFile(String path, String url, String branchName, String username, String accessToken) {
        try {
            new GitWrapper()
                    .gitInit(new File(path), branchName)
                    .gitAdd()
                    .gitCommit(username, "SpringIntegSupport@microsoft.com")
                    .gitPush(url, username, accessToken)
                    .gitClean();
        } catch (Exception e) {
            throw new RuntimeException("Error while pushing workflow file", e);
        }
    }

    public void createFederatedCredential(OAuth2AuthorizedClient management, String appName, String tenantId, String objectId, String username, String pathName, String branchName) {
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

    public static GraphServiceClient<Request> getGraphClient(OAuth2AuthorizedClient management, String tenantId) {
        final List<String> scopes = List.of(GRAPH_SCOPE);
        final ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                .clientId(management.getClientRegistration().getClientId()).tenantId(tenantId).clientSecret(management.getClientRegistration().getClientSecret()).build();
        final TokenCredentialAuthProvider authProvider = new TokenCredentialAuthProvider(
                scopes, clientSecretCredential);
        return GraphServiceClient.builder().authenticationProvider(authProvider).buildClient();
    }

    public String getGitHubActionStatus(String username, String pathName, String accessToken) {
        WebClient webClient = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer
                                .defaultCodecs()
                                .maxInMemorySize(16 * 1024 * 1024))
                        .build())
                .build();
        GitHubActionRunStatus gitHubActionRunStatus = webClient
                .get()
                .uri(String.format(WORKFLOW_PATH, username, pathName, WORKFLOW_FILE_PATH, "runs"))
                .header("Authorization", "token " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(GitHubActionRunStatus.class)
                .block();
        assert gitHubActionRunStatus != null;
        return gitHubActionRunStatus.getWorkflowRuns().get(0).getStatus();
    }

    public void startGitHubAction(String username, String pathName, String accessToken, String branchName) {
        Map<String, String> map = new HashMap<>();
        map.put("ref", branchName);
        try {
            webClient
                    .post()
                    .uri(String.format(WORKFLOW_PATH, username, pathName, WORKFLOW_FILE_PATH, "dispatches"))
                    .header("Authorization", "token " + accessToken)
                    .body(BodyInserters.fromValue(map))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            throw new RuntimeException("Error while starting GitHub action", e);
        }
    }

    private String encryptSecretValue(String secretValue, String key) throws SodiumException {
        SodiumJava sodium = new SodiumJava();
        LazySodiumJava lazySodium = new LazySodiumJava(sodium, StandardCharsets.UTF_8);
        String ciphertext = lazySodium.cryptoBoxSealEasy(secretValue, Key.fromBase64String(key));
        return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(ciphertext));
    }

}
