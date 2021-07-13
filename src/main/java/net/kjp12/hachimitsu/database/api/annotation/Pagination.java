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

package net.kjp12.hachimitsu.database.api.annotation;// Created 2021-30-06T14:14:14

import net.kjp12.hachimitsu.database.impl.C0;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables pagination on the output.
 * <p>
 * Results will be up to the {@link #limit() limit} from a given
 * {@link #offset() offset}.
 *
 * @author KJP12
 * @since ${version}
 **/
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Pagination {
	/**
	 * Enables sorting of the results when defined.
	 *
	 * @implNote Descending order is used by the driver.
	 */
	Value sort() default @Value({});

	/**
	 * Enables limiting how many results are sent to the user.
	 *
	 * @implSpec Unless otherwise allowed by your SQL database or driver, the value
	 *           referenced must be an integer.
	 * @see Query#values()
	 * @see C0
	 */
	String limit() default "";

	/**
	 * Enables offsetting the results by an amount.
	 *
	 * @implSpec Unless otherwise allowed by your SQL database or driver, the value
	 *           referenced must be an integer.
	 * @see Query#values()
	 * @see C0
	 */
	String offset() default "";
}
