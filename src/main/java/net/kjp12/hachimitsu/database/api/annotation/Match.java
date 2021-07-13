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

package net.kjp12.hachimitsu.database.api.annotation;// Created 2021-14-06T14:56:15

import java.lang.annotation.Target;

/**
 * @author KJP12
 * @since ${version}
 **/
@Target({})
public @interface Match {
	/** Table to match against. */
	int table() default 0;

	/** Column of the {@link #table() matching table} to match against */
	String primary() default "";

	/** Column of the {@link Table current table} to match against */
	String secondary() default "";
}
