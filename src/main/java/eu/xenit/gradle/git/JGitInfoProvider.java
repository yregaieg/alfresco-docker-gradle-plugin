package eu.xenit.gradle.git;

import eu.xenit.gradle.JenkinsUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by thijs on 1/23/17.
 */
public class JGitInfoProvider implements GitInfoProvider {
    public static JGitInfoProvider GetProviderForProject(Project project){
        Path projectFolder = project.getProjectDir().toPath().toAbsolutePath();
        Path gitFolder = Paths.get(".git");
        while(Files.notExists(projectFolder.resolve(gitFolder))){
            projectFolder = projectFolder.getParent();
            if(projectFolder == null){
                return null;
            }
        }

        try {
            JGitInfoProvider gitInfoProvider = new JGitInfoProvider(projectFolder.resolve(gitFolder).toFile());
            return gitInfoProvider;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
    private Repository gitRepo;
    private Git git;

    public JGitInfoProvider(File gitFolder) throws IOException {
        this.gitRepo = new FileRepository(gitFolder);
        this.git = new Git(gitRepo);
    }

    @Override
    public String getBranch(){
        //workaround because Jenkins uses git in detached head state
        if(!"local".equals(JenkinsUtil.getBranch())){
            return JenkinsUtil.getBranch();
        }
        try {
            return gitRepo.getBranch();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getCommitChecksum() {
        final RevCommit commit = getLastRevCommit();
        return commit.getName();
    }

    private RevCommit getLastRevCommit() {
        final RevCommit[] commit = new RevCommit[1];
        try {
            git.log().setMaxCount(1).call().forEach(revCommit -> {
                commit[0] = revCommit;
            });
        } catch (GitAPIException e) {
            throw new IllegalStateException(e);
        }
        return commit[0];
    }

    @Override
    public String getOrigin(){
        final URIish origin = getUrIish();
        if(origin == null)
            return null;
        return origin.toString();
    }

    private URIish getUrIish() {
        final URIish[] origin = new URIish[1];
        try {
            git.remoteList().call().forEach(remoteConfig -> {
                if("origin".equals(remoteConfig.getName())){
                    origin[0] = remoteConfig.getURIs().get(0);
                }
            });
        } catch (GitAPIException e) {
            throw new IllegalStateException(e);
        }
        return origin[0];
    }

    @Override
    public URL getCommitURL() throws CannotConvertToUrlException {
        final URIish origin = getUrIish();
        final URL url;
        String path = origin.getPath().split("\\.git\\z")[0];
        if(path.startsWith("/")){
            path = path.substring(1);
        }
        try {
            if("bitbucket.org".equals(origin.getHost())) {
                url = new URL("https", origin.getHost(), "/" + path + "/commits/" + getCommitChecksum());
            } else if("github.com".equals(origin.getHost())) {
                url = new URL("https", origin.getHost(), "/" + path + "/commit/" + getCommitChecksum());
            } else {
                throw new CannotConvertToUrlException("The host is unknown");
            }
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        return url;
    }

    @Override
    public String getCommitAuthor() {
        PersonIdent person = getLastRevCommit().getAuthorIdent();
        return person.getName()+" <"+person.getEmailAddress()+">";
    }

    @Override
    public String getCommitMessage() {
        return getLastRevCommit().getFullMessage().trim();
    }

}
