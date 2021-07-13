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

package net.kjp12.hachimitsu.database.api.annotation;// Created 2021-14-06T14:56:23

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author KJP12
 * @since ${version}
 **/
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface Value {
	/**
	 * The originating table. Defaults to the primary table.
	 */
	int table() default 0;

	/**
	 * The path to the value.
	 * <p>
	 * If the array is larger than one entry, the value will be converted into
	 * nested sub-queries up to the last entry.
	 * <p>
	 * When given <code>{"cause_pos", "x"}</code>, the selection will be converted
	 * into <code>(cause_pos).x</code> when inserted into the SQL statement.
	 * <p>
	 * If the {@link #table() table} isn't the primary table, a letter will be
	 * assigned starting at <code>a</code>. A possible outcome can be
	 * <code>a.x</code>, or with a nested selection, <code>(a.cause_pos).x</code>.
	 */
	String[] value();
}
