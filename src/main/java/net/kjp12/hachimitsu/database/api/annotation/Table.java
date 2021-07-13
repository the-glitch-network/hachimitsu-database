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

package net.kjp12.hachimitsu.database.api.annotation;// Created 2021-14-06T14:56:31

import java.lang.annotation.*;

/**
 * @author KJP12
 * @since ${version}
 **/
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Tables.class)
public @interface Table {
	/** Table ID */
	int table() default 0;

	/** Table name */
	String value();

	/**
	 * Columns to check against within the join.
	 *
	 * @implNote This <em>always</em> does an <code>left outer join</code>, and as
	 *           such, will never cause a row to be omitted.
	 */
	Match match() default @Match;
}
