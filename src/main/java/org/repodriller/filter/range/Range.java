package org.repodriller.filter.range;

import java.util.ArrayList;
import java.util.List;

import org.repodriller.domain.ChangeSet;
import org.repodriller.scm.SCM;

public class Range implements CommitRange {

	private String last;
	private String first;

	public Range(String first, String last) {
		this.first = first;
		this.last = last;
	}

	@Override
	public List<ChangeSet> get(SCM scm) {
		List<ChangeSet> all = scm.getChangeSets();

		List<ChangeSet> filtered = new ArrayList<ChangeSet>();
		boolean started = false;
		String firstFound = null;
		
		for(ChangeSet cs : all) {
			if(!started && cs.getId().equals(last)) {
				started = true;
				firstFound = last;
			}
			if(!started && cs.getId().equals(first)) {
				started = true;
				firstFound = first;
			}
			
			if(started) filtered.add(cs);
			if(started && firstFound.equals(last) && cs.getId().equals(first)) break;
			if(started && firstFound.equals(first) && cs.getId().equals(last)) break;
		}
		
		return filtered;
	}

}
