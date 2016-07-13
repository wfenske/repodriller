/**
 * Copyright 2014 Maurício Aniche

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package br.com.metricminer2.scm;

import java.util.List;

import br.com.metricminer2.domain.ChangeSet;
import br.com.metricminer2.domain.Commit;

public interface SCM {
	List<ChangeSet> getChangeSets();
	Commit getCommit(String id);
	ChangeSet getHead();
	List<RepositoryFile> files();
	long totalCommits();
	void reset();
	void checkout(String id);
	String blame(String file, String currentCommit, Integer line);

    /**
     * Disable getting branch information for a commit. This operation is
     * <em>optional</em> (depends on the repository type), but will never throw
     * an error. Invoking this method on a large GIT repository may
     * significantly improve the analysis speed.
     * 
     * @return the SCM on which the method was invoked
     */
    SCM disableBranches();
}
