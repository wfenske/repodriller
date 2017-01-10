package org.repodriller.filter.range;

import java.util.Calendar;
import java.util.List;

public class Commits {

	public static CommitRange all() {
		return new AllCommits();
	}
	
	public static CommitRange onlyInHead() {
		return new OnlyInHead();
	}

	public static CommitRange single(String commit) {
		return new SingleCommit(commit);
	}

	public static CommitRange since(Calendar date) {
		return new SinceCommit(date);
	}

	public static CommitRange monthly(int months) {
		return new MonthlyCommits(months);
	}

	public static CommitRange betweenDates(Calendar from, Calendar to) {
		return new BetweenDates(from, to);
	}
	
	public static CommitRange list(List<String> commits) {
		return new ListOfCommits(commits);
	}

	public static CommitRange range(String start, String end) {
		return new Range(start, end);
	}
}
