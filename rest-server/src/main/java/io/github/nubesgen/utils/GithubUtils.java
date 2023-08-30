package io.github.nubesgen.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.eclipse.jgit.api.Git;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class GithubUtils {

    /**
     * Get the username from the GitHub URL.
     *
     * @param url The GitHub URL.
     * @return The username.
     */
    public static String getUserName(String url) {
        int beginIndex = StringUtils.ordinalIndexOf(url, "/", 3);
        int endIndex = StringUtils.ordinalIndexOf(url, "/", 4);
        return url.substring(beginIndex + 1, endIndex);
    }

    /**
     * Get the repository name from the GitHub URL.
     *
     * @param url The GitHub URL.
     * @return The repository name.
     */
    public static String getPathName(String url){
        int beginIndex = StringUtils.ordinalIndexOf(url, "/", 4);
        int endIndex;
        if(url.contains("&")) {
            endIndex = StringUtils.ordinalIndexOf(url, "&", 1);
            return url.substring(beginIndex + 1, endIndex);
        } else {
            return url.substring(beginIndex + 1);
        }
    }

    /**
     * Download the source code from the GitHub repository.
     *
     * @param url The git repository url.
     * @param branchName The branch name.
     * @return The path of the source code.
     */
    public static synchronized String downloadSourceCodeFromGitHub(String url, String branchName) {
        String repositoryPath = getPathName(url);
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
            throw new RuntimeException(e);
        } finally {
            if (git != null) {
                git.close();
                pathName = git.getRepository().getWorkTree().toString();
            }
        }
        return pathName;
    }

    /**
     * Delete the directory.
     *
     * @param directory The directory.
     */
    public static synchronized void deleteRepositoryDirectory(File directory) {
        File tempGitDirectory;
        try {
            tempGitDirectory = new File(directory.toString());
            if (tempGitDirectory.exists()) {
                FileUtils.deleteDirectory(tempGitDirectory);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
