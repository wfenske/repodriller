#!/usr/bin/env sh

SKUNK_CP=""

add_to_cp()
{
    if [ -n "$1" ]
    then
	if [ ! -e "$1" ]
	then
	    echo "Warning: class path entry does not exist: \`$1'" >&2
	fi
	SKUNK_CP="$SKUNK_CP${SKUNK_CP:+:}${1}"
    fi
}

me_dir=$(dirname "$0")
skunk_dir="$me_dir"/..
maven_repo=$HOME/.m2/repository

add_to_cp "${me_dir}"/target/classes

add_to_cp "${maven_repo}"/log4j/log4j/1.2.14/log4j-1.2.14.jar

add_to_cp "${maven_repo}"/org/eclipse/jgit/org.eclipse.jgit/3.4.1.201406201815-r/org.eclipse.jgit-3.4.1.201406201815-r.jar

add_to_cp "${maven_repo}"/com/google/guava/guava/18.0/guava-18.0.jar

add_to_cp "${maven_repo}"/com/google/collections/google-collections/1.0/google-collections-1.0.jar

add_to_cp "${maven_repo}"/commons-cli/commons-cli/1.3/commons-cli-1.3.jar

add_to_cp "${maven_repo}"/org/apache/commons/commons-lang3/3.3.2/commons-lang3-3.3.2.jar

#add_to_cp "${maven_repo}"/br/usp/metricminer/2.4.2-SNAPSHOT-no-git-branches/metricminer-2.4.2-SNAPSHOT-no-git-branches.jar
add_to_cp "${maven_repo}"/org/repodriller/repodriller/1.2.2-SNAPSHOT-no-git-branches/repodriller-1.2.2-SNAPSHOT-no-git-branches.jar

exec java -cp $SKUNK_CP de.ovgu.skunk.ListCommits "$@"
