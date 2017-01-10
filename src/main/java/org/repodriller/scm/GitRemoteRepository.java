package org.repodriller.scm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.repodriller.domain.ChangeSet;
import org.repodriller.domain.Commit;

public class GitRemoteRepository implements SCM {
	
	private GitRepository tempGitRepository;
	private String remoteRepositoryUrl;
	private String tempGitPath;

	private static Logger log = Logger.getLogger(GitRemoteRepository.class);
	
	public GitRemoteRepository(String url) {
		this(url, gitSystemTempDir(), false);
	}
	
	public GitRemoteRepository(String url, String rootTempGitPath, boolean bare) {
		try {
			this.remoteRepositoryUrl = url;
			if(rootTempGitPath == null) {
				rootTempGitPath = gitSystemTempDir();
			}
			this.tempGitPath = gitRemoteRepositoryTempDir(url, rootTempGitPath);
			this.initTempGitRepository(bare);
			this.tempGitRepository = new GitRepository(new File(tempGitPath).getCanonicalPath());
		} catch (Exception e) {
			log.error("Git remote repository initialization", e);
			throw new RuntimeException(e);
		}
	}
	
	protected void initTempGitRepository(boolean bare) throws GitAPIException {
		File directory = new File(this.tempGitPath);
		
		if(!directory.exists()) {
			log.info("Cloning Remote Repository " + this.remoteRepositoryUrl + " into " + this.tempGitPath);
			Git.cloneRepository()
					.setURI(this.remoteRepositoryUrl)
					.setBare(bare)
					.setDirectory(directory)
					.setCloneAllBranches(true)
					.setNoCheckout(false)
					.call();
		}
	}

	protected static String gitSystemTempDir() {
		return FileUtils.getTempDirectory().getAbsolutePath();
	}
	
	protected static String gitRemoteRepositoryTempDir(String remoteRepositoryUrl, String rootTempDir) {
		int lastIndexOfDotGit = remoteRepositoryUrl.lastIndexOf(".git");
		if(lastIndexOfDotGit < 0 )
			lastIndexOfDotGit = remoteRepositoryUrl.length();
		String directoryName = remoteRepositoryUrl.substring(remoteRepositoryUrl.lastIndexOf("/")+1, lastIndexOfDotGit);

		if(!rootTempDir.endsWith(File.separator))
			rootTempDir += File.separator;
		
		return rootTempDir + directoryName;
	}
	
	public static SCMRepository singleProject(String url) {
		return singleProject(url, gitSystemTempDir(), false);
	}
	
	protected static SCMRepository singleProject(String url, String rootTempGitPath, boolean bare) {
		return new GitRemoteRepository(url, rootTempGitPath, bare).info();
	}
	
	public static SCMRepository[] allProjectsIn(List<String> urls) {
		return allProjectsIn(urls, gitSystemTempDir(), false);
	}
	
	protected static SCMRepository[] allProjectsIn(List<String> urls, String rootTempGitPath, boolean bare) {
		List<SCMRepository> repos = new ArrayList<SCMRepository>();
		for (String url : urls) {
			repos.add(singleProject(url, rootTempGitPath, bare));
		}
		
		return repos.toArray(new SCMRepository[repos.size()]);
	}
	
	public void deleteTempGitPath() throws IOException {
		FileUtils.deleteDirectory(new File(this.tempGitPath));
	}

	@Override
	public SCMRepository info() {
		return tempGitRepository.info();
	}

	@Override
	public ChangeSet getHead() {
		return tempGitRepository.getHead();
	}

	@Override
	public List<ChangeSet> getChangeSets() {
		return tempGitRepository.getChangeSets();
	}

	@Override
	public Commit getCommit(String id) {
		return tempGitRepository.getCommit(id);
	}

	@Override
	public void checkout(String hash) {
		tempGitRepository.checkout(hash);
	}

	@Override
	public List<RepositoryFile> files() {
		return tempGitRepository.files();
	}

	@Override
	public void reset() {
		tempGitRepository.reset();
	}

	@Override
	public long totalCommits() {
		return tempGitRepository.totalCommits();
	}

	@Override
	@Deprecated
	public String blame(String file, String currentCommit, Integer line) {
		return tempGitRepository.blame(file, currentCommit, line);
	}

	@Override
	public List<BlamedLine> blame(String file, String commitToBeBlamed, boolean priorCommit) {
		return tempGitRepository.blame(file, commitToBeBlamed, priorCommit);
	}
	
	public static SingleGitRemoteRepositoryBuilder hostedOn(String gitUrl) {
		return new SingleGitRemoteRepositoryBuilder(gitUrl);
	}

	public static MultipleGitRemoteRepositoryBuilder hostedOn(List<String> gitUrls) {
		return new MultipleGitRemoteRepositoryBuilder(gitUrls);
	}
	
}
