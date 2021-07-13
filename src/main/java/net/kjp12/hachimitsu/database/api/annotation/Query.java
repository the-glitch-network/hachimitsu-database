/* Copyright 2021 KJP12
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */

package net.kjp12.hachimitsu.database.api.annotation;// Created 2021-18-06T01:24:12

import net.kjp12.hachimitsu.database.api.DatabaseRecord;
import net.kjp12.hachimitsu.database.impl.C0;
import net.kjp12.hachimitsu.database.impl.ClassMap;

import java.lang.annotation.*;

/**
 * @author KJP12
 * @since ${version}
 **/
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Queries.class)
public @interface Query {
	/**
	 * Mini code snippets referencing off the input {@link DatabaseRecord record}.
	 * <p>
	 * You can optionally dereference off of the SQL provider by prefixing with
	 * <code>^</code>.
	 * <p>
	 * Local variable support is available with loading via <code>&lt;0</code> and
	 * storing via <code>&gt;0</code>. A check can be added on load to continue
	 * execution by appending <code>?</code> right after the number. If the variable
	 * was not stored before, the following code will execute.
	 *
	 * @implNote The value referenced must either be mapped by {@link ClassMap} or
	 *           be supported by your database driver. There is no distinction
	 *           between certain types such as byte arrays/binary streams and blobs,
	 *           and as such, will default to get/setBinaryStream when provided with
	 *           an InputStream.
	 * @see C0
	 */
	String[] values();

	/**
	 * Raw JDBC compliant SQL where query parameter, optionally using <code>?</code>
	 * as a template key.
	 *
	 * There must be as many {@link #values() values} as there are available .
	 */
	String query();

	/**
	 * Mask for the flag. May be used standalone, or with {@link #maskRq()} for
	 * multiple flags.
	 */
	int mask();

	/**
	 * Required flags in order to add the query.
	 */
	int maskRq() default -1;
}
