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

package br.com.metricminer2.persistence.csv;

import java.io.File;
import java.io.PrintStream;

import br.com.metricminer2.persistence.PersistenceMechanism;

public class CSVFile implements PersistenceMechanism {

	private PrintStream ps;

	public CSVFile(File file) {
		try {
			ps = new PrintStream(file);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public CSVFile(String fileName) {
		this(new File(fileName));
	}

	public CSVFile(String path, String name) {
		this(new File(new File(path), name));
	}

	@Override
	public synchronized void write(Object... line) {

		boolean first = true;
		for (Object o : line) {
			if (!first)
				ps.print(",");
			ps.print(o);
			first = false;
		}

		ps.println();
		ps.flush();

	}

	@Override
	public void close() {
		ps.close();
	}

}
