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

package org.repodriller.persistence;

public interface PersistenceMechanism {
    /**
     * <p>Writes the elements into a row of the output file.</p>
     * <p><code>null</code> will appear as null in the output. All other objects will be converted to a string with the
     * help of Object{@link #toString()}. The string values will be appropriately CSV-escaped.</p>
     * <p>Calls to this method are thread-safe.</p>
     *
     * @param line
     */
    void write(Object... line);

    void close();
}
