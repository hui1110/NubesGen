package io.github.nubesgen.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class that provides GitHub related operations.
 */
public final class GithubUtils {

    /**
     * Get the username from the GitHub URL.
     *
     * @param url the GitHub repository URL.
     * @return the GitHub username.
     */
    public static String getGitHubUserName(String url) {
        int beginIndex = StringUtils.ordinalIndexOf(url, "/", 3);
        int endIndex = StringUtils.ordinalIndexOf(url, "/", 4);
        return url.substring(beginIndex + 1, endIndex);
    }

    /**
     * Get the repository name from the GitHub URL.
     *
     * @param url the GitHub repository URL.
     * @return the GitHub repository name.
     */
    public static String getGitHubRepositoryName(String url){
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
    public static Map<String, String> downloadSourceCodeFromGitHub(String url, String branchName) {
        Map<String, String> result = new HashMap<>();
        branchName = Objects.equals(branchName, "null") ? null : branchName;
        String repositoryName = getGitHubRepositoryName(url);
        String userName = getGitHubUserName(url);
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        // support deploy the same repository source code to different app
        String directory = repositoryName.concat("-").concat(userName)
                .concat("-").concat(String.valueOf(timestamp.getTime()));
        Git git = null;
        String pathName;
        try {
            git = Git.cloneRepository()
                    .setURI(url)
                    .setBranch(branchName)
                    .setDirectory(new File(directory))
                    .call();
            pathName = git.getRepository().getWorkTree().toString();
            if(branchName == null){
                Ref ref = git.getRepository().findRef(Constants.HEAD).getTarget();
                branchName = ref.getName().substring(ref.getName().lastIndexOf("/")+1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
        result.put("pathName", pathName);
        result.put("branchName", branchName);
        return result;
    }

    /**
     * Delete the directory.
     *
     * @param directory The directory.
     */
    public static void deleteRepositoryDirectory(String directory) {
        try {
            File tempGitDirectory = new File(directory);
            if (tempGitDirectory.exists()) {
                FileUtils.deleteDirectory(tempGitDirectory);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
