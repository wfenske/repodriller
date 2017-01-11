/**
 *
 */
package de.ovgu.skunk;

import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.repodriller.RepositoryMining;
import org.repodriller.Study;
import org.repodriller.domain.Commit;
import org.repodriller.domain.Modification;
import org.repodriller.filter.range.Commits;
import org.repodriller.persistence.PersistenceMechanism;
import org.repodriller.persistence.csv.CSVFile;
import org.repodriller.scm.CommitVisitor;
import org.repodriller.scm.GitRepository;
import org.repodriller.scm.SCMRepository;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author wfenske
 */
public class ListCommits implements Study {
    public static final Logger LOG = Logger.getLogger(ListCommits.class);

    static final String BASE_CSV_HEADER = "Hash,FilesModified,LinesAdded,LinesRemoved,LinesDelta,Message,Filenames";
    static final String CSV_HEADER_WITH_BRANCHES = BASE_CSV_HEADER + ",Branches";

    static class Config {
        public static final char OPT_HELP = 'h';
        public static final String OPT_HELP_L = "help";
        public static final char OPT_REPO = 'r';
        public static final String OPT_REPO_L = "repo";
        public static final char OPT_OUTPUT = 'o';
        public static final String OPT_OUTPUT_L = "output";
        public static final char OPT_FORCE = 'f';
        public static final String OPT_FORCE_L = "force";
        //public static final char OPT_BRANCHES = 'b';
        //public static final String OPT_BRANCHES_L = "branches";

        public String repoDir = null;
        public boolean force;
        public String outputFilename;

        public void validateRepoDir() {
            File repoFile = new File(repoDir);
            if (!repoFile.exists()) {
                throw new IllegalArgumentException(
                        "Repository directory does not exist: " + repoDir);
            }
            if (!repoFile.isDirectory()) {
                throw new IllegalArgumentException("Repository is not a directory: " + repoDir);
            }
        }

        public void validateOutputFilename() {
            File outputFile = new File(outputFilename);
            if (outputFile.exists()) {
                if (outputFile.isFile()) {
                    if (!force) {
                        throw new IllegalArgumentException("Refusing to overwrite output file: "
                                + outputFilename + " (specify `--" + OPT_FORCE_L + "' to overwrite.)");
                    }
                } else if (outputFile.isDirectory()) {
                    throw new IllegalArgumentException(
                            "Output file exists and is a directory: " + outputFilename);
                }
            }
        }

    }

    private Config config;

    /**
     * @param args
     */
    public static void main(String[] args) {
        ListCommits mainClass = new ListCommits();
        try {
            mainClass.parseCommandLineArgs(args);
        } catch (Exception e) {
            System.err.println("Error while processing command line arguments: " + e);
            e.printStackTrace();
            System.err.flush();
            System.out.flush();
            System.exit(1);
        }
        mainClass.execute();
    }

    static class SkunkCommitVisitor implements CommitVisitor {
        private final Pattern hgImportPattern = Pattern.compile("^--HG--.*|^branch :.*|^extra :.*",
                Pattern.MULTILINE);
        private final Pattern csvProblemChars = Pattern.compile("[\"'\n\r,]");

        private int commitCount = 0;

        public SkunkCommitVisitor() {
        }

        private static class CommitStats {
            int linesAdded = 0;
            int linesRemoved = 0;
            Set<String> filesModified = new LinkedHashSet<>();

            private CommitStats() {
                /* don't call me from the outside! */
            }

            public int linesDelta() {
                return linesAdded - linesRemoved;
            }

            public static final CommitStats from(Commit commit) {
                CommitStats result = new CommitStats();
                result.initializeFrom(commit);
                return result;
            }

            private void initializeFrom(Commit commit) {

                for (Modification m : commit.getModifications()) {
                    linesAdded += m.getAdded();
                    linesRemoved += m.getRemoved();

                    // If the file was deleted, then newPath will be /dev/null.
                    // We cannot use that. Since we are interested in file
                    // modifications (which includes file deletions), we take
                    // oldPath as the file name in that case.
                    String filename = m.getNewPath();
                    if ("/dev/null".equals(filename))
                        filename = m.getOldPath();
                    if (isValidFileName(filename)) {
                        filesModified.add(filename);
                    }
                }
            }

            private boolean isValidFileName(String fileName) {
                if (fileName == null || fileName.isEmpty())
                    return false;
                if ("/dev/null".equals(fileName))
                    return false;
                return true;
            }

        }

        @Override
        public void process(SCMRepository repo, Commit commit, PersistenceMechanism writer) {
            commitCount++;
            LOG.debug("Commit " + commitCount);

            String cleanMsg = cleanUpCommitMessage(commit);
            if (cleanMsg == null || cleanMsg.isEmpty()) {
                LOG.warn("Empty commit message, discarding commit " + commit);
            } else {
                actuallyProcessCommit(commit, cleanMsg, writer);
            }

            if (commitCount % 1000 == 0) {
                LOG.info("Processed " + commitCount + " commits.");
            }
        }

        private void actuallyProcessCommit(Commit commit, String cleanMsg,
                                           PersistenceMechanism writer) {
            CommitStats stats = CommitStats.from(commit);

            // LOG.debug("" + msg + " -> " + cleanMsg);
            List<Object> line = new ArrayList<>();
            line.add(commit.getHash());
            line.add(commit.getModifications().size());
            // line.add(stats.filesModified);
            line.add(stats.linesAdded);
            line.add(stats.linesRemoved);
            line.add(stats.linesDelta());
            line.add(cleanMsg);
            line.add(filenamesToCsv(stats.filesModified));
            line.add(branchesToCsv(commit));
            writer.write(line.toArray());
        }

        private String filenamesToCsv(Set<String> filesModified) {
            StringBuilder result = new StringBuilder();
            boolean first = true;
            for (String filename : filesModified) {
                if (first) {
                    first = false;
                } else {
                    result.append(':');
                }
                String cleanName = removeSpecialCsvChars(filename);
                result.append(cleanName);
            }
            return result.toString();
        }

        private String branchesToCsv(Commit commit) {
            StringBuilder result = new StringBuilder();
            boolean first = true;
            for (String branchName : commit.getBranches()) {
                if (first) {
                    first = false;
                } else {
                    result.append(':');
                }
                String cleanBranchName = removeSpecialCsvChars(branchName);
                result.append(cleanBranchName);
            }
            return result.toString();
        }

        private String removeSpecialCsvChars(String text) {
            Matcher m = csvProblemChars.matcher(text);
            return m.replaceAll(" ");
        }

        private String cleanUpCommitMessage(Commit commit) {
            String msg = commit.getMsg();
            if (msg == null || msg.isEmpty()) {
                return msg;
            }
            Matcher hgImportMatcher = hgImportPattern.matcher(msg);
            String cleanMsg = hgImportMatcher.replaceAll("");
            cleanMsg = removeSpecialCsvChars(cleanMsg).replaceAll("\\s+", " ").trim();
            return cleanMsg;
        }

        @Override
        public String name() {
            return this.getClass().getSimpleName();
        }
    }

    @Override
    public void execute() {
        File outputFile = ensureOutputFile();
        PersistenceMechanism writer = new CSVFile(outputFile.getPath());
        writer.write(CSV_HEADER_WITH_BRANCHES);
        final SCMRepository repo = GitRepository.singleProject(config.repoDir);
        // @formatter:off
		new RepositoryMining()
		.in(repo).through(Commits.all())
		.process(new SkunkCommitVisitor(), writer)
		.mine();
		// @formatter:on
    }

    private File ensureOutputFile() {
        File outputFile = new File(config.outputFilename);
        if (!outputFile.exists()) {
            return createOutputFileOrDie(outputFile);
        }

        if (outputFile.isFile() && outputFile.length() > 0) {
            if (!outputFile.delete()) {
                throw new RuntimeException("Failed to delete non-empty output file " + outputFile.getAbsolutePath());
            }
            return createOutputFileOrDie(outputFile);
        } else {
            if (!outputFile.canWrite()) {
                throw new RuntimeException("Cannot write to output file " + outputFile.getAbsolutePath());
            }
            // File is either empty or is a special file, such as /dev/stdout.
            return outputFile;
        }
    }

    private File createOutputFileOrDie(File outputFile) {
        try {
            if (!outputFile.createNewFile()) {
                throw new RuntimeException("Failed to create output file " + outputFile.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create empty output file " + outputFile.getAbsolutePath(),
                    e);
        }
        return outputFile;
    }

    /**
     * Analyze input to decide what to do during runtime
     *
     * @param args the command line arguments
     */
    private void parseCommandLineArgs(String[] args) {

        CommandLineParser parser = new DefaultParser();
        Options fakeOptionsForHelp = makeOptions(true);
        Options actualOptions = makeOptions(false);

        CommandLine line;
        try {
            CommandLine dummyLine = parser.parse(fakeOptionsForHelp, args);
            if (dummyLine.hasOption(Config.OPT_HELP)) {
                System.out.flush();
                System.err.flush();
                HelpFormatter formatter = new HelpFormatter();
                //@formatter:off
				formatter.printHelp(progName()
						+ " [-" + Config.OPT_HELP + "]"
						+ " [-" + Config.OPT_FORCE + "]"
						//+ " [-" + Config.OPT_BRANCHES + "]"
						+ " -" + Config.OPT_REPO + " DIR"
						+ " -" + Config.OPT_OUTPUT + " FILE"
						, "Extract metrics from GIT commits and store them in a CSV file." /* header */
						, actualOptions
						, null /* footer */
						);
				//@formatter:on
                System.out.flush();
                System.err.flush();
                System.exit(0);
                // We never actually get here due to the preceding
                // System.exit(int) call.
                return;
            }
            line = parser.parse(actualOptions, args);
        } catch (ParseException e) {
            System.out.flush();
            System.err.flush();
            System.err.println("Error in command line: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printUsage(new PrintWriter(System.err, true), 80, progName(), actualOptions);
            System.out.flush();
            System.err.flush();
            System.exit(1);
            // We never actually get here due to the preceding System.exit(int)
            // call.
            return;
        }

        this.config = new Config();

        config.force = line.hasOption(Config.OPT_FORCE);
        config.repoDir = line.getOptionValue(Config.OPT_REPO);
        config.outputFilename = line.getOptionValue(Config.OPT_OUTPUT);

        config.validateRepoDir();
        config.validateOutputFilename();
    }

    private Options makeOptions(boolean forHelp) {
        boolean required = !forHelp;

        Options options = new Options();
        // @formatter:off

		// --help= option
		options.addOption(Option.builder(String.valueOf(Config.OPT_HELP))
				.longOpt(Config.OPT_HELP_L)
				.desc("print this help sceen and exit")
				.build());

		options.addOption(Option.builder(String.valueOf(Config.OPT_REPO))
				.longOpt(Config.OPT_REPO_L)
				.desc("directory containing the git repository to analyze")
				.hasArg().argName("DIR")
				.required(required)
				.build());

		options.addOption(Option.builder(String.valueOf(Config.OPT_OUTPUT))
				.longOpt(Config.OPT_OUTPUT_L)
				.desc("name of output CSV file")
				.hasArg().argName("FILE")
				.required(required)
				.build());

		options.addOption(Option.builder(String.valueOf(Config.OPT_FORCE))
				.longOpt(Config.OPT_FORCE_L)
				.desc("overwrite the output file if it already exists")
				.build());

//		options.addOption(Option.builder(String.valueOf(Config.OPT_BRANCHES))
//				.longOpt(Config.OPT_BRANCHES_L)
//				.desc("include the original branch of each commit.  This is a slow operation and therefore disabled by default.")
//				.build());

		// @formatter:on
        return options;
    }

    private String progName() {
        return this.getClass().getSimpleName();
    }

}
