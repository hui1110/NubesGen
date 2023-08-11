package io.github.nubesgen.model;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.net.URISyntaxException;

public class GitWrapper {

    private Git git;

    /**
     * Git init.
     * @param directory Git directory
     * @param initialBranch Initial branch
     * @return A wrapper for Git
     * @throws GitAPIException Git init failed
     */
    public GitWrapper gitInit(File directory, String initialBranch) throws GitAPIException {
        this.git = Git.init()
                .setInitialBranch(initialBranch)
                .setDirectory(directory)
                .call();
        return this;
    }

    /**
     * Git add.
     *
     * @return A wrapper for Git
     * @throws GitAPIException Git add failed
     */
    public GitWrapper gitAdd() throws GitAPIException {
        this.git.add().addFilepattern(".").call();
        return this;
    }

    /**
     * Git commit.
     *
     * @param userName GitHub username
     * @param email GitHub email
     * @return A wrapper for Git
     * @throws GitAPIException Git commit failed
     */
    public GitWrapper gitCommit(String userName, String email) throws GitAPIException {
        git.commit()
                .setMessage("Initial commit from Azure Spring Apps button")
                .setAuthor(userName, email)
                .setCommitter(userName, email)
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
    public GitWrapper gitPush(
            String gitRepoUrl,
            String userName,
            String accessToken)
            throws GitAPIException, URISyntaxException {
        RemoteAddCommand remote = git.remoteAdd();
        remote.setName("origin").setUri(new URIish(gitRepoUrl)).call();
        PushCommand pushCommand = git.push();
        pushCommand.add("HEAD");
        pushCommand.setRemote("origin");
        pushCommand.setCredentialsProvider(
                new UsernamePasswordCredentialsProvider(userName, accessToken));
        pushCommand.call();
        return this;
    }

    /**
     * Clean the git repo.
     *
     * @throws GitAPIException clean or close failed
     */
    public void gitClean() throws GitAPIException {
        git.clean().call();
        git.close();
    }

}
