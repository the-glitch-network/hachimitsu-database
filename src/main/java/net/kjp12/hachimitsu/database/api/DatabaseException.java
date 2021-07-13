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

package net.kjp12.hachimitsu.database.api;// Created 2021-11-07T05:19:47

/**
 * @author KJP12
 * @since ${version}
 **/
public class DatabaseException extends Exception {
	public DatabaseException(Throwable cause, Object related) {
		super("Object/message related to " + cause + ": " + related, cause);
	}

	public DatabaseException(Throwable cause, Object... related) {
		super(format(cause, related), cause);
	}

	private static String format(Object original, Object... objects) {
		var builder = new StringBuilder("Objects and messages related to ").append(original).append("...");
		for (var object : objects) {
			builder.append("\n - ").append(object);
		}
		return builder.toString();
	}
}
