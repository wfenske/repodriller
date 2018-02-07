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

package org.repodriller.domain;

import java.util.*;


public class Commit {

    private String hash;
    private Developer author;
    private Developer committer;
    private String msg;
    private List<Modification> modifications;
    private List<String> parents;
    private Calendar date;
    private Set<String> branches;
    private boolean merge;
    private boolean inMainBranch;
    private TimeZone authorTimeZone;
    private TimeZone committerTimeZone;
    private Calendar committerDate;

    public Commit(String hash, Developer author, Developer committer, Calendar authorDate, Calendar committerDate, String msg, List<String> parents) {
        this(hash, author, committer, authorDate, TimeZone.getDefault(), committerDate, TimeZone.getDefault(), msg, parents, false, new HashSet<>(), false);
    }

    public Commit(String hash, Developer author, Developer committer, Calendar authorDate, TimeZone authorTimeZone, Calendar committerDate, TimeZone committerTimeZone, String msg, List<String> parents, boolean merge, Set<String> branches, boolean isCommitInMainBranch) {
        this.hash = hash;
        this.author = author;
        this.committer = committer;
        this.date = authorDate;
        this.committerDate = committerDate;
        this.msg = msg;
        this.parents = parents;
        this.merge = merge;
        this.authorTimeZone = authorTimeZone;
        this.committerTimeZone = committerTimeZone;
        this.modifications = new ArrayList<Modification>();
        this.branches = branches;
        this.inMainBranch = isCommitInMainBranch;
    }

    public boolean isMerge() {
        return merge;
    }

    public String getHash() {
        return hash;
    }

    public Developer getAuthor() {
        return author;
    }

    public String getMsg() {
        return msg;
    }

    public Developer getCommitter() {
        return committer;
    }

    /**
     * @return The hash of the first parent commit, if a parent exists; otherwise, the empty string
     */
    public String getParent() {
        if (parents.isEmpty()) return "";
        return parents.get(0);
    }

    /**
     * @return An unmodifiable, possibly empty set of hashes of the parents of this commit
     */
    public Collection<String> getParents() {
        return parents;
    }

    public void addModification(String oldPath, String newPath, ModificationType change, String diff, String sc) {
        Modification m = new Modification(oldPath, newPath, change, diff, sc);
        modifications.add(m);

    }

    public void addModifications(List<Modification> modifications) {
        this.modifications.addAll(modifications);
    }

    public List<Modification> getModifications() {
        return Collections.unmodifiableList(modifications);
    }

    public Calendar getCommitterDate() {
        return committerDate;
    }

    @Override
    public String toString() {
        return "Commit [hash=" + hash + ", parents=" + parents + ", author=" + author + ", msg=" + msg + ", modifications="
                + modifications + "]";
    }

    public TimeZone getAuthorTimeZone() {
        return authorTimeZone;
    }

    public TimeZone getCommitterTimeZone() {
        return committerTimeZone;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof Commit)) {
            return false;
        } else if (other == this) {
            return true;
        } else {
            Commit c = (Commit) other;
            return this.getHash().equals(c.getHash());
        }
    }

    public Calendar getDate() {
        return date;
    }

    public Set<String> getBranches() {
        return Collections.unmodifiableSet(branches);
    }

    public boolean isInMainBranch() {
        return inMainBranch;
    }

}
