package io.github.nubesgen.web;

import io.github.nubesgen.service.github.GitHubActionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/github")
public class GitHubActionController {

    private static final String DEFAULT_OAUTH2_CLIENT = "management";
    private final GitHubActionService gitHubActionService;

    GitHubActionController(GitHubActionService gitHubActionService) {
        this.gitHubActionService = gitHubActionService;
    }

    @GetMapping("/createGitHubAction")
    public @ResponseBody ResponseEntity<?> createGitHubAction(@RegisteredOAuth2AuthorizedClient(DEFAULT_OAUTH2_CLIENT) OAuth2AuthorizedClient management, @RequestParam String subscriptionId, @RequestParam String authorizationCode, @RequestParam String url, @RequestParam String branchName, @RequestParam String serviceName, @RequestParam String appName) {
        try {
            gitHubActionService.addActionsWorkflow(management, subscriptionId, authorizationCode, url, branchName, serviceName, appName);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}