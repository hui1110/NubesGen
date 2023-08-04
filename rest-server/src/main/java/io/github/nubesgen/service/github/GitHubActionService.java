package io.github.nubesgen.service.github;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.core.util.Context;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.azure.resourcemanager.authorization.AuthorizationManager;
import com.azure.resourcemanager.authorization.fluent.models.RoleAssignmentInner;
import com.azure.resourcemanager.authorization.models.PrincipalType;
import com.azure.resourcemanager.authorization.models.RoleAssignmentCreateParameters;
import com.azure.resourcemanager.authorization.models.ServicePrincipal;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import com.goterl.lazysodium.exceptions.SodiumException;
import com.goterl.lazysodium.utils.Key;
import io.github.nubesgen.utils.ASADeployUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLOutput;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public final class GitHubActionService {

    private static final String BASE_URI = "https://github.com";
    private static final String API_BASE_URI = "https://api.github.com";
    private static final String GET_USER_PATH = API_BASE_URI + "/user";
    private static final String ACCESS_TOKEN_PATH = BASE_URI + "/login/oauth/access_token";
    private static final String CREATE_REPO_SECRET_PATH = API_BASE_URI + "/repos/%s/%s/actions/secrets/%s";
    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubActionService.class);

    private final WebClient webClient;

    public GitHubActionService(WebClient webClient) {
        this.webClient = webClient;
    }

    public void addActionsWorkflow(OAuth2AuthorizedClient management, String subscriptionId, String code, String url, String branchName, String serviceName, String appName) throws IOException, SodiumException {
        branchName = Objects.equals(branchName, "null") ? null : branchName;
        String accessToken = getAccessToken(code);
        GitHubUser user = getUser(accessToken);
        LOGGER.info("User {} is authenticated.", user.getUsername());

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
        LOGGER.info("Cloned repository to {}", pathName);

        assert pathName != null;
        File path = new File(pathName);
        Path project = path.toPath();
        Path output = project.resolve(".github/workflows/" + "deploy-source-code-to-asa.yml");
        if (!Files.exists(output)) {
            Files.createDirectories(output.getParent());
            Files.createFile(output);
        } else {
            Files.delete(output);
            Files.createFile(output);
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:workflows/" + "deploy-source-code-to-asa.yml");
        FileCopyUtils.copy(resource.getInputStream(), Files.newOutputStream(output, StandardOpenOption.APPEND));
        String templateYaml = Files.readString(output);
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output))) {
            String yaml = templateYaml.replace("${push-branches}", branchName == null ? "main" : branchName);
            writer.write(yaml);
        }
        LOGGER.info("Created workflow file");

        RepoPublicKey repoPublicKey  = webClient
                .get()
                .uri(String.format(CREATE_REPO_SECRET_PATH, user.getUsername(), path.getName(), "public-key"))
                .header("Authorization", "token " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(RepoPublicKey.class)
                .block();
        assert repoPublicKey != null;
        LOGGER.info("Created public key {}", repoPublicKey.getKeyId());

        Map<String, String> map = ASADeployUtils.getCredential(management, subscriptionId);
        map.put("AZURE_SPRING_SERVICE_NAME", serviceName);
        map.put("AZURE_SPRING_APP_NAME", appName);

        Map<String, String> secretMap = new HashMap<>();
        secretMap.put("key_id", repoPublicKey.getKeyId());

        SodiumJava sodium = new SodiumJava();
        LazySodiumJava lazySodium = new LazySodiumJava(sodium, StandardCharsets.UTF_8);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String ciphertext = lazySodium.cryptoBoxSealEasy(entry.getValue(), Key.fromBase64String(repoPublicKey.getKey()));
            String encryptedValue = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(ciphertext));
            secretMap.put("encrypted_value", encryptedValue);
            try {
            String urlPath = String.format(CREATE_REPO_SECRET_PATH, user.getUsername(), path.getName(), entry.getKey());
               webClient
                        .put()
                        .uri(urlPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(secretMap))
                        .header("Authorization", "token " + accessToken)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve().toBodilessEntity().block();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        LOGGER.info("Created secrets");

        try {
            AuthorizationManager authorizationManager =

            TokenCredential tokenCredential = new DefaultAzureCredentialBuilder().build();
            TokenRequestContext request = new TokenRequestContext().addScopes("https://management.azure.com/.default");
            AccessToken token =
                    tokenCredential.getToken(request).retry(3L).blockOptional().orElseThrow(() -> new RuntimeException(
                            "Couldn't retrieve JWT"));
            final TokenCredential credential = ASADeployUtils.toTokenCredential(token.getToken());

            final AzureProfile azureProfile = new AzureProfile(AzureEnvironment.AZURE);
            AzureResourceManager azureResourceManager = AzureResourceManager.authenticate(credential, azureProfile).withSubscription(subscriptionId);

            azureResourceManager
                    .accessManagement()
                    .roleAssignments()
                    .manager()
                    .roleServiceClient()
                    .getRoleAssignments()
                    .deleteWithResponse(
                            "/subscriptions/" + subscriptionId,
                            "05c5a614-a7d6-4502-b150-c2fb455033ff",
                            null,
                            Context.NONE);
            LOGGER.info("Deleted role assignment");

            azureResourceManager.accessManagement()
                    .roleAssignments()
                    .manager()
                    .roleServiceClient()
                    .getRoleAssignments()
                    .createWithResponse(
                            "/subscriptions/" + subscriptionId,
                            "05c5a614-a7d6-4502-b150-c2fb455033ff",
                            new RoleAssignmentCreateParameters()
                                    .withRoleDefinitionId(
                                    "/subscriptions/" + subscriptionId + "/providers/Microsoft.Authorization/roleDefinitions/b24988ac-6180-42a0-ab88-20f7382dd24c")
                                    .withPrincipalId("86df03f6-a6c6-4e37-86a9-21a91db85841")
                                    .withPrincipalType(PrincipalType.SERVICE_PRINCIPAL),
                            Context.NONE);
            LOGGER.info("Assigned role to service principal");
        }catch (Exception e) {
            e.printStackTrace();
        }

        try {
            new GitWrapper()
                    .gitInit(path, branchName == null ? "main": branchName)
                    .gitAdd()
                    .gitCommit(user.getUsername())
                    .gitPush(url, user.getUsername(), accessToken)
                    .gitClean();
        } catch (Exception e) {
            e.printStackTrace();
        }
        LOGGER.info("Pushed to the repo");
    }

    private GitHubUser getUser(String code) {
        try {
            return webClient
                    .get()
                    .uri(GET_USER_PATH)
                    .header("Authorization", "token " + code)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(GitHubUser.class)
                    .block();
        } catch (RuntimeException ex) {
            throw new RuntimeException("An error occurred while getting user info.", ex);
        }
    }

    private String getAccessToken(String authorizationCode) {

        TokenResult result;
        LOGGER.info("Getting access token");
        try {
            Map<String, String> map = new HashMap<>();
            map.put("client_id", "27c83ffc0f8fd2a9859b");
            map.put("client_secret", "3b449f0bfc7ea1b2bd1e006f3e7e62f6c23e6c72");
            map.put("redirect_uri", "http://localhost:8080/progress.html");
            map.put("code", authorizationCode);
            result = webClient.post()
                    .uri(ACCESS_TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(map))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(TokenResult.class)
                    .block();
        } catch (RuntimeException ex) {
            LOGGER.error("Error while authenticating", ex);
            throw new RuntimeException("Error while authenticating");
        }
        if (result == null || result.getAccessToken() == null) {
            throw new RuntimeException("Error while authenticating");
        }
        LOGGER.info("Access token: {}", result.getAccessToken());
        return result.getAccessToken();
    }

    private static class GitWrapper{
        private Git repo;

        private GitWrapper gitInit(File directory, String initialBranch) throws GitAPIException {
            this.repo = Git.init()
                    .setInitialBranch(initialBranch)
                    .setDirectory(directory)
                    .call();
            return this;
        }

        private GitWrapper gitAdd() throws GitAPIException {
            this.repo.add().addFilepattern(".").call();
            return this;
        }

        private GitWrapper gitCommit(String userName) throws GitAPIException {
            repo.commit()
                    .setMessage("Initial commit from Azure Spring Apps")
                    .setAuthor(userName, "hui1110rui@163.com")
                    .setCommitter(userName, "hui1110rui@163.com")
                    .setSign(false)
                    .call();
            return this;
        }

        /**
         * @param gitRepoUrl Remote gitRepoUrl
         * @param userName Username
         * @param accessToken accessToken to access remote Git service
         * @return A wrapper for Git
         * @throws URISyntaxException java.net.URISyntaxException
         */
        private GitWrapper gitPush(
                String gitRepoUrl,
                String userName,
                String accessToken)
                throws GitAPIException, URISyntaxException {
            RemoteAddCommand remote = repo.remoteAdd();
            remote.setName("origin").setUri(new URIish(gitRepoUrl)).call();
            PushCommand pushCommand = repo.push();
            pushCommand.add("HEAD");
            pushCommand.setRemote("origin");
            pushCommand.setCredentialsProvider(
                    new UsernamePasswordCredentialsProvider(userName, accessToken));
            pushCommand.call();
            return this;
        }

        private void gitClean() throws GitAPIException {
            repo.clean().call();
            repo.close();
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CreatedSecrets {

        @JsonAlias("name")
       private String name;

        @JsonAlias("created_at")
       private String created_at;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCreated_at() {
            return created_at;
        }

        public void setCreated_at(String created_at) {
            this.created_at = created_at;
        }

        public String getUpdated_at() {
            return updated_at;
        }

        public void setUpdated_at(String updated_at) {
            this.updated_at = updated_at;
        }

        @JsonAlias("updated_at")
       private String updated_at;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RepoPublicKey {

        @JsonAlias("key")
        private String key;

        @JsonAlias("key_id")
        private String keyId;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }
    }
}



