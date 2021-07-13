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

package net.kjp12.hachimitsu.database.impl;// Created 2021-20-06T10:25:13

import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.IdentityHashMap;

/**
 * @author KJP12
 * @since ${version}
 **/
public enum ClassMap {
	VOID(void.class, "setObject", "getObject", Opcodes.ALOAD, Opcodes.ASTORE, true, false),
	BOOLEAN(boolean.class, "setBoolean", "getBoolean", Opcodes.ILOAD, Opcodes.ISTORE),
	BYTE(byte.class, "setByte", "getByte", Opcodes.ILOAD, Opcodes.ISTORE),
	SHORT(short.class, "setShort", "getShort", Opcodes.ILOAD, Opcodes.ISTORE),
	INT(int.class, "setInt", "getInt", Opcodes.ILOAD, Opcodes.ISTORE),
	LONG(long.class, "setLong", "getLong", Opcodes.LLOAD, Opcodes.LSTORE),
	FLOAT(float.class, "setFloat", "getFloat", Opcodes.FLOAD, Opcodes.FSTORE),
	DOUBLE(double.class, "setDouble", "getDouble", Opcodes.DLOAD, Opcodes.DSTORE),
	STRING(String.class, "setString", "getString"),
	TIMESTAMP(Timestamp.class, "setTimestamp", "getTimestamp"),
	INPUT_STREAM(InputStream.class, "setBinaryStream", "getBinaryStream");

	static final IdentityHashMap<Class<?>, ClassMap> intern = new IdentityHashMap<>();
	public final Class<?> internal;
	public final String setter, getter;
	public final boolean passClass, tryBox;
	public final int load, store;

	ClassMap(Class<?> internal, String setter, String getter, int load, int store, boolean passClass, boolean tryBox) {
		this.internal = internal;
		this.setter = setter;
		this.getter = getter;
		this.load = load;
		this.store = store;
		this.passClass = passClass;
		this.tryBox = tryBox;
	}

	ClassMap(Class<?> internal, String setter, String getter, int load, int store) {
		this(internal, setter, getter, load, store, false, false);
	}

	ClassMap(Class<?> internal, String setter, String getter) {
		this(internal, setter, getter, Opcodes.ALOAD, Opcodes.ASTORE, false, false);
	}

	static {
		for (var v : values()) {
			intern.put(v.internal, v);
		}
	}

	public static ClassMap findMapper(Class<?> clazz) {
		return intern.getOrDefault(clazz, VOID);
	}
}
