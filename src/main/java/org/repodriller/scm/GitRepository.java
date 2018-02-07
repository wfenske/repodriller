/**
 * Copyright 2014 Maurício Aniche
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.repodriller.scm;

import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.CannotDeleteCurrentBranchException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.repodriller.domain.ChangeSet;
import org.repodriller.domain.Commit;
import org.repodriller.domain.Developer;
import org.repodriller.domain.ModificationType;
import org.repodriller.util.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class GitRepository implements SCM {

    private static final int MAX_SIZE_OF_A_DIFF = 100000;
    private static final int DEFAULT_MAX_NUMBER_OF_FILES_IN_A_COMMIT = 5000;
    private static final String BRANCH_MM = "mm";
    private static final Set<String> UNDEFINED_BRANCHES = Collections.singleton("branch-info-omitted");

    private String path;
    private String mainBranchName;
    private int maxNumberFilesInACommit;
    private int maxSizeOfDiff;

    private static Logger log = Logger.getLogger(GitRepository.class);
    private boolean firstParentOnly;
    private boolean omitBranches = false;

    public GitRepository(String path, boolean firstParentOnly) {
        this.path = path;
        this.firstParentOnly = firstParentOnly;
        this.maxNumberFilesInACommit = checkMaxNumberOfFiles();
        this.maxSizeOfDiff = checkMaxSizeOfDiff();
    }

    public GitRepository(String path) {
        this(path, false);
    }

    public void omitBranches() {
        this.omitBranches = true;
    }

    private int checkMaxNumberOfFiles() {
        String prop = System.getProperty("git.maxfiles");
        if (prop == null) {
            return DEFAULT_MAX_NUMBER_OF_FILES_IN_A_COMMIT;
        }
        return Integer.parseInt(prop);
    }

    private int checkMaxSizeOfDiff() {
        String prop = System.getProperty("git.maxdiff");
        if (prop == null) {
            return MAX_SIZE_OF_A_DIFF;
        }
        return Integer.parseInt(prop);
    }

    public static SCMRepository singleProject(String path) {
        return new GitRepository(path).info();
    }

    public static SCMRepository singleProject(String path, boolean singleParentOnly) {
        return new GitRepository(path, singleParentOnly).info();
    }

    public static SCMRepository[] allProjectsIn(String path) {
        return allProjectsIn(path, false);
    }

    public static SCMRepository[] allProjectsIn(String path, boolean singleParentOnly) {
        List<SCMRepository> repos = new ArrayList<SCMRepository>();

        for (String dir : FileUtils.getAllDirsIn(path)) {
            repos.add(singleProject(dir, singleParentOnly));
        }

        return repos.toArray(new SCMRepository[repos.size()]);
    }

    public SCMRepository info() {
        RevWalk rw = null;
        Git git = null;
        try {
            git = openRepository();
            AnyObjectId headId = git.getRepository().resolve(Constants.HEAD);

            rw = new RevWalk(git.getRepository());
            RevCommit root = rw.parseCommit(headId);
            rw.sort(RevSort.REVERSE);
            rw.markStart(root);
            RevCommit lastCommit = rw.next();

            String origin = git.getRepository().getConfig().getString("remote", "origin", "url");

            return new SCMRepository(this, origin, path, headId.getName(), lastCommit.getName());
        } catch (Exception e) {
            throw new RuntimeException("error when info " + path, e);
        } finally {
            if (rw != null)
                rw.release();
            if (git != null)
                git.close();
        }

    }

    protected Git openRepository() throws IOException, GitAPIException {
        Git git = Git.open(new File(path));
        if (this.mainBranchName == null) {
            this.mainBranchName = discoverMainBranchName(git);
        }
        return git;
    }

    private String discoverMainBranchName(Git git) throws IOException {
        return git.getRepository().getBranch();
    }

    @Override
    public ChangeSet getHead() {
        Git git = null;
        try {
            git = openRepository();
            ObjectId head = git.getRepository().resolve(Constants.HEAD);

            RevWalk revWalk = new RevWalk(git.getRepository());
            RevCommit r = revWalk.parseCommit(head);
            return new ChangeSet(r.getName(), convertToDate(r));

        } catch (Exception e) {
            throw new RuntimeException("error in getHead() for " + path, e);
        } finally {
            if (git != null)
                git.close();
        }

    }

    @Override
    public List<ChangeSet> getChangeSets() {
        Git git = null;
        try {
            git = openRepository();

            List<ChangeSet> allCs;
            if (!firstParentOnly) allCs = getAllCommits(git);
            else allCs = firstParentsOnly(git);

            return allCs;
        } catch (Exception e) {
            throw new RuntimeException("error in getChangeSets for " + path, e);
        } finally {
            if (git != null)
                git.close();
        }
    }

    private List<ChangeSet> firstParentsOnly(Git git) {

        try {
            List<ChangeSet> allCs = new ArrayList<ChangeSet>();

            RevWalk revWalk = new RevWalk(git.getRepository());
            revWalk.setRevFilter(new FirstParentFilter());
            revWalk.sort(RevSort.TOPO);
            Ref headRef = git.getRepository().getRef(Constants.HEAD);
            RevCommit headCommit = revWalk.parseCommit(headRef.getObjectId());
            revWalk.markStart(headCommit);
            for (RevCommit revCommit : revWalk) {
                allCs.add(extractChangeSet(revCommit));
            }

            return allCs;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<ChangeSet> getAllCommits(Git git) throws GitAPIException, NoHeadException, IOException {
        List<ChangeSet> allCs = new ArrayList<ChangeSet>();

        for (RevCommit r : git.log().all().call()) {
            allCs.add(extractChangeSet(r));
        }
        return allCs;
    }

    private ChangeSet extractChangeSet(RevCommit r) {
        String hash = r.getName();
        GregorianCalendar date = convertToDate(r);

        ChangeSet cs = new ChangeSet(hash, date);
        return cs;
    }

    private GregorianCalendar convertToDate(RevCommit revCommit) {
        GregorianCalendar date = new GregorianCalendar();
        date.setTimeZone(revCommit.getAuthorIdent().getTimeZone());
        date.setTime(revCommit.getAuthorIdent().getWhen());

        return date;
    }

    ThreadLocal<Git> git = ThreadLocal.withInitial(new Supplier<Git>() {
        @Override
        public Git get() {
            try {
                return openRepository();
            } catch (IOException | GitAPIException e) {
                throw new RuntimeException("Failed to open repository.", e);
            }
        }
    });

    ThreadLocal<Repository> repo = ThreadLocal.withInitial(new Supplier<Repository>() {
        @Override
        public Repository get() {
            return git.get().getRepository();
        }
    });

    @Override
    public Commit getCommit(String id) {
        try {
            Git git = this.git.get();
            Repository repo = this.repo.get();
            Iterable<RevCommit> commits = git.log().add(repo.resolve(id)).call();
            Commit theCommit = null;

            for (RevCommit jgitCommit : commits) {

                Developer author = new Developer(jgitCommit.getAuthorIdent().getName(),
                        jgitCommit.getAuthorIdent().getEmailAddress());
                Developer committer = new Developer(jgitCommit.getCommitterIdent().getName(),
                        jgitCommit.getCommitterIdent().getEmailAddress());

                TimeZone authorTimeZone = jgitCommit.getAuthorIdent().getTimeZone();
                TimeZone committerTimeZone = jgitCommit.getCommitterIdent().getTimeZone();

                String msg = jgitCommit.getFullMessage().trim();
                final String hash = getCommitHash(jgitCommit);
                final List<String> parents = getParents(jgitCommit);

                GregorianCalendar authorDate = new GregorianCalendar();
                authorDate.setTime(jgitCommit.getAuthorIdent().getWhen());
                authorDate.setTimeZone(jgitCommit.getAuthorIdent().getTimeZone());

                GregorianCalendar committerDate = new GregorianCalendar();
                committerDate.setTime(jgitCommit.getCommitterIdent().getWhen());
                committerDate.setTimeZone(jgitCommit.getCommitterIdent().getTimeZone());

                boolean merge = false;
                if (jgitCommit.getParentCount() > 1) merge = true;

                final Set<String> branches;
                final boolean isCommitInMainBranch;

                if (omitBranches) {
                    branches = UNDEFINED_BRANCHES;
                    isCommitInMainBranch = true;
                } else {
                    branches = getBranches(git, hash);
                    isCommitInMainBranch = branches.contains(this.mainBranchName);
                }

                theCommit = new Commit(hash, author, committer, authorDate, authorTimeZone, committerDate, committerTimeZone, msg, parents, merge, branches, isCommitInMainBranch);

                List<DiffEntry> diffsForTheCommit = diffsForTheCommit(repo, jgitCommit);
                if (diffsForTheCommit.size() > this.getMaxNumberFilesInACommit()) {
                    log.warn("commit " + id + " has more than files than the limit");
                    throw new RuntimeException("commit " + id + " too big (too many files), sorry");
                }

                for (DiffEntry diff : diffsForTheCommit) {

                    ModificationType change = Enum.valueOf(ModificationType.class,
                            diff.getChangeType().toString());

                    String oldPath = diff.getOldPath();
                    String newPath = diff.getNewPath();

                    String diffText = "";
                    String sc = "";
                    if (diff.getChangeType() != ChangeType.DELETE) {
                        diffText = getDiffText(repo, diff);
                        sc = getSourceCode(repo, diff);
                    }

                    if (diffText.length() > maxSizeOfDiff) {
                        log.error("diff for " + newPath + " too big");
                        diffText = "-- TOO BIG --";
                    }

                    theCommit.addModification(oldPath, newPath, change, diffText, sc);

                }

                break;
            }

            return theCommit;
        } catch (Exception e) {
            throw new RuntimeException("error detailing " + id + " in " + path, e);
        } finally {
        }
    }

    private static String getCommitHash(RevCommit jgitCommit) {
        return jgitCommit.getName().toString();
    }

    private List<String> getParents(RevCommit jgitCommit) {
        final List<String> parents;
        final int parentCount = jgitCommit.getParentCount();
        switch (parentCount) {
            case 0:
                parents = Collections.emptyList();
                break;
            case 1:
                parents = Collections.singletonList(getCommitHash(jgitCommit.getParent(0)));
                break;
            default:
                parents = new ArrayList<>(parentCount);
                for (int i = 0; i < parentCount; i++) {
                    parents.add(getCommitHash(jgitCommit.getParent(i)));
                }
        }
        return parents;
    }

    private Set<String> getBranches(Git git, String hash) throws GitAPIException {
        List<Ref> gitBranches = git.branchList().setContains(hash).call();
        Set<String> mappedBranches = gitBranches.stream()
                .map(
                        (ref) -> ref.getName().substring(ref.getName().lastIndexOf("/") + 1))
                .collect(Collectors.toSet());
        return mappedBranches;
    }

    private List<DiffEntry> diffsForTheCommit(Repository repo, RevCommit commit)
            throws IOException, AmbiguousObjectException, IncorrectObjectTypeException {

        AnyObjectId currentCommit = repo.resolve(commit.getName());
        AnyObjectId parentCommit = commit.getParentCount() > 0
                ? repo.resolve(commit.getParent(0).getName()) : null;

        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setBinaryFileThreshold(2 * 1024); // 2 mb max a file
        df.setRepository(repo);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setDetectRenames(true);
        setContext(df);

        List<DiffEntry> diffs = null;

        if (parentCommit == null) {
            RevWalk rw = new RevWalk(repo);
            diffs = df.scan(new EmptyTreeIterator(),
                    new CanonicalTreeParser(null, rw.getObjectReader(), commit.getTree()));
            rw.release();
        } else {
            diffs = df.scan(parentCommit, currentCommit);
        }

        df.release();

        return diffs;
    }

    private void setContext(DiffFormatter df) {
        String context = System.getProperty("git.diffcontext");
        if (context == null) return;
        df.setContext(Integer.parseInt(System.getProperty("git.diffcontext")));
    }

    private String getSourceCode(Repository repo, DiffEntry diff) throws MissingObjectException, IOException, UnsupportedEncodingException {
        try {
            ObjectReader reader = repo.newObjectReader();
            byte[] bytes = reader.open(diff.getNewId().toObjectId()).getBytes();
            return new String(bytes, "utf-8");
        } catch (Throwable e) {
            return "";
        }
    }

    private String getDiffText(Repository repo, DiffEntry diff)
            throws IOException, UnsupportedEncodingException {
        DiffFormatter df2 = null;
        try {
            String diffText;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            df2 = new DiffFormatter(out);
            df2.setRepository(repo);
            df2.format(diff);
            diffText = out.toString("UTF-8");
            return diffText;
        } catch (Throwable e) {
            return "";
        } finally {
            if (df2 != null)
                df2.release();
        }
    }

    public synchronized void checkout(String hash) {
        Git git = null;
        try {
            git = openRepository();
            git.reset().setMode(ResetType.HARD).call();
            git.checkout().setName(mainBranchName).call();
            deleteMMBranch(git);
            git.checkout().setCreateBranch(true).setName(BRANCH_MM).setStartPoint(hash).setForce(true).setOrphan(true).call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (git != null)
                git.close();
        }
    }

    private synchronized void deleteMMBranch(Git git) throws GitAPIException, NotMergedException, CannotDeleteCurrentBranchException {
        List<Ref> refs = git.branchList().call();
        for (Ref r : refs) {
            if (r.getName().endsWith(BRANCH_MM)) {
                git.branchDelete().setBranchNames(BRANCH_MM).setForce(true).call();
                break;
            }
        }
    }

    @Override
    public List<RepositoryFile> files() {
        List<RepositoryFile> all = new ArrayList<RepositoryFile>();
        for (File f : getAllFilesInPath()) {
            all.add(new RepositoryFile(f));
        }

        return all;
    }

    public synchronized void reset() {
        Git git = null;
        try {
            git = openRepository();

            git.checkout().setName(mainBranchName).setForce(true).call();
            git.branchDelete().setBranchNames(BRANCH_MM).setForce(true).call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (git != null)
                git.close();
        }

    }

    private List<File> getAllFilesInPath() {
        return FileUtils.getAllFilesInPath(path);
    }

    @Override
    public long totalCommits() {
        return getChangeSets().size();
    }

    @Override
    @Deprecated
    public String blame(String file, String commitToBeBlamed, Integer line) {
        return blame(file, commitToBeBlamed).get(line).getCommit();
    }

    public List<BlamedLine> blame(String file, String commitToBeBlamed) {
        return blame(file, commitToBeBlamed, true);
    }

    public List<BlamedLine> blame(String file, String commitToBeBlamed, boolean priorCommit) {
        Git git = null;
        try {
            git = openRepository();

            ObjectId gitCommitToBeBlamed;
            if (priorCommit) {
                Iterable<RevCommit> commits = git.log().add(git.getRepository().resolve(commitToBeBlamed)).call();
                gitCommitToBeBlamed = commits.iterator().next().getParent(0).getId();
            } else {
                gitCommitToBeBlamed = git.getRepository().resolve(commitToBeBlamed);
            }

            BlameResult blameResult = git.blame().setFilePath(file).setStartCommit(gitCommitToBeBlamed).setFollowFileRenames(true).call();
            if (blameResult != null) {
                int rows = blameResult.getResultContents().size();
                List<BlamedLine> result = new ArrayList<>();
                for (int i = 0; i < rows; i++) {
                    result.add(new BlamedLine(i,
                            blameResult.getResultContents().getString(i),
                            blameResult.getSourceAuthor(i).getName(),
                            blameResult.getSourceCommitter(i).getName(),
                            blameResult.getSourceCommit(i).getId().getName()));
                }

                return result;
            } else {
                throw new RuntimeException("BlameResult not found.");
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    public Integer getMaxNumberFilesInACommit() {
        return maxNumberFilesInACommit;
    }

}
