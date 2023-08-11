package io.github.nubesgen.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public class GitHubTokenResult {
    @JsonAlias("access_token")
    private String accessToken;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
