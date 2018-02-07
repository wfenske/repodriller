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

package org.repodriller.persistence.csv;

import org.apache.commons.lang3.StringEscapeUtils;
import org.repodriller.persistence.PersistenceMechanism;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class CSVFile implements PersistenceMechanism {

    private PrintStream ps;

    private String[] header = null;


    public CSVFile(String fileName) {
        this(fileName, false);
    }

    public CSVFile(String fileName, boolean append) {
        try {
            ps = new PrintStream(new FileOutputStream(fileName, append));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CSVFile(String path, String name) {
        this(path, name, false);
    }

    public CSVFile(String path, String name, boolean append) {
        this(verifyPath(path) + name, append);
    }

    public CSVFile(String path, String name, String[] header) {
        this(verifyPath(path) + name, header);
    }

    public CSVFile(String fileName, String[] header) {
        this(fileName, header, false);
    }

    public CSVFile(String fileName, String[] header, boolean append) {
        this.header = header;
        try {
            ps = new PrintStream(new FileOutputStream(fileName, append));
            if (header != null) {
                printHeader(ps, header);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void printHeader(PrintStream ps, String[] header) {
        if (header.length == 0) {
            ps.println();
        } else {
            printElt(ps, header[0]);
            for (int i = 1; i < header.length; i++) {
                ps.print(',');
                ps.print(header[i]);
            }
            ps.println();
        }
    }

    private static void printElt(PrintStream ps, Object element) {
        if (element == null) ps.print("null");
        else {
            String field = element.toString();
            String escaped = StringEscapeUtils.escapeCsv(field);
            ps.print(escaped);
        }
    }

    @Override
    public synchronized void write(Object... line) throws CSVFileFormatException {
        if (header != null && header.length != line.length)
            throw new CSVFileFormatException("CSV Header Columns Number Differs From Writer Columns Number.");

        if (line.length > 0) {
            printElt(ps, line[0]);
            for (int i = 1; i < line.length; i++) {
                ps.print(",");
                printElt(ps, line[i]);
            }
        }

        ps.println();
        ps.flush();

    }

    public void close() {
        ps.close();
    }

    private static String verifyPath(String path) {
        char lastchar = path.charAt(path.length() - 1);
        if (lastchar != File.separatorChar)
            path = path + File.separator;
        return path;
    }
}
