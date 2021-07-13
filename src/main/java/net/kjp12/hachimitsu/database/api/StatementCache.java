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

package net.kjp12.hachimitsu.database.api;// Created 2021-05-06T15:00:03

import net.kjp12.hachimitsu.database.api.annotation.Pagination;
import net.kjp12.hachimitsu.database.api.annotation.Query;
import net.kjp12.hachimitsu.database.api.annotation.Table;
import net.kjp12.hachimitsu.database.api.annotation.Value;
import net.kjp12.hachimitsu.database.impl.C0;
import net.kjp12.hachimitsu.database.impl.ClassMap;
import net.kjp12.hachimitsu.database.impl.StatementHandler;
import org.objectweb.asm.*;

import java.io.FileOutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * @author KJP12
 * @since ${version}
 **/
public final class StatementCache<I extends DatabaseRecord<?>> { // I - Input | O - Output
	private static final MethodHandles.Lookup SELF = MethodHandles.lookup();
	private static final VarHandle TABLE = MethodHandles.arrayElementVarHandle(HandlerEntry[].class);

	private static final String STATEMENT_TYPE = Type.getInternalName(PreparedStatement.class),
			STATEMENT_DESCRIPTOR = Type.getDescriptor(PreparedStatement.class),
			RESULT_SET_TYPE = Type.getInternalName(ResultSet.class),
			RESULT_SET_DESCRIPTOR = Type.getDescriptor(ResultSet.class);

	private static final Type STRING_TYPE = Type.getType(String.class);

	private final ReferenceQueue<StatementHandler> queue = new ReferenceQueue<>();
	private HandlerEntry[] table = new HandlerEntry[16];
	private final SqlConnectionProvider sqlImpl;
	private final Method proxy;
	private final Class<I> iClass;

	public StatementCache(SqlConnectionProvider sqlImpl, Class<I> iClass, Method proxy) {
		this.sqlImpl = sqlImpl;
		this.iClass = iClass;
		this.proxy = proxy;
	}

	/**
	 * Closes all prepared statements within the cache.
	 *
	 * @throws DatabaseException if any exception gets thrown while processing.
	 */
	public void reload() throws DatabaseException {
		ArrayList<Throwable> l = null;
		try {
			for (var entry : table) {
				if (entry == null) {
					continue;
				}
				try {
					entry.close();
				} catch (Exception e) {
					if (l == null) {
						l = new ArrayList<>();
					}
					l.add(new DatabaseException(e, entry));
				}
			}
			if (l != null) {
				// This allows a bit smarter of stack omission by having one carrier to suppress
				// the multiple stacks.
				var t = new Throwable("Issues closing multiple prepared statements...");
				for (var p : l) {
					t.addSuppressed(p);
				}
				t.printStackTrace();
			}
		} catch (Exception e) {
			if (l != null) {
				for (var p : l) {
					e.addSuppressed(p);
				}
			}
			throw new DatabaseException(e, Arrays.toString(table));
		}
	}

	public void handle(I i) throws DatabaseException {
		// Before anything, clean the table.
		cleanTable();
		int flags = i.flags();
		int index = flags & (table.length - 1);
		var entry = table[index];
		StatementHandler handler;
		if (entry == null || entry.refersTo(null)) {
			handler = add(i, index);
		} else if (entry.flags != flags) {
			handler = add(i, flags & resize(flags));
		} else {
			handler = entry.get();
			if (handler == null) {
				handler = add(i, index);
			}
		}
		handler.query(i);
	}

	private void cleanTable() {
		int hash = table.length - 1;
		HandlerEntry entry;
		while ((entry = (HandlerEntry) queue.poll()) != null) {
			try {
				entry.close();
			} catch (DatabaseException e) {
				e.printStackTrace();
			} finally {
				TABLE.compareAndSet(table, entry.flags & hash, entry, null);
			}
		}
	}

	private StatementHandler add(I i, int index) throws DatabaseException {
		var handler = surrogate(i);
		table[index] = new HandlerEntry(i.flags(), handler, queue);
		return handler;
	}

	private int resize(int flags) {
		int hash;
		do {
			var old = table;
			int length = old.length << 1;
			table = new HandlerEntry[length];
			hash = length - 1;
			for (var e : old) {
				if (e == null || e.refersTo(null)) {
					continue;
				}
				table[e.flags & hash] = e;
			}
		} while (table[flags & hash] != null);
		return hash;
	}

	private StatementHandler surrogate(I i) throws DatabaseException {
		var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		var handlerType = Type.getType(StatementHandler.class);
		var iType = Type.getType(iClass);
		var iDesc = Type.getDescriptor(iClass);
		var iName = Type.getInternalName(iClass);
		var provider = Type.getType(SqlConnectionProvider.class);
		var statementHandler = handlerType.getInternalName();
		var self = "net/kjp12/hachimitsu/database/api/StatementHandler$" + i.getClass().getSimpleName() + '$'
				+ i.flags();
		var sqlQuery = new StringBuilder();
		byte[] array;

		Table[] tables = proxy.getAnnotationsByType(Table.class);
		Arrays.sort(tables, Comparator.comparingInt(Table::table));

		writer.visit(Opcodes.V11, Opcodes.ACC_SYNTHETIC | Opcodes.ACC_FINAL, self, null, statementHandler, null);

		{ // Query function, overrides the interface.
			var qDesc = Type.getMethodDescriptor(Type.VOID_TYPE, iType);
			var synth = writer.visitMethod(Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE, "query",
					Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Object.class)), null, null);
			synth.visitVarInsn(Opcodes.ALOAD, 0);
			synth.visitVarInsn(Opcodes.ALOAD, 1);
			synth.visitTypeInsn(Opcodes.CHECKCAST, iName);
			synth.visitMethodInsn(Opcodes.INVOKEVIRTUAL, self, "query", qDesc, false);
			synth.visitInsn(Opcodes.RETURN);
			synth.visitMaxs(0, 0);
			synth.visitEnd();

			var query = writer.visitMethod(Opcodes.ACC_FINAL, "query", qDesc, null, null);

			// Execute the query then store at 2, replacing the statement.
			query.visitVarInsn(Opcodes.ALOAD, 0);
			query.visitVarInsn(Opcodes.ALOAD, 1);
			query.visitMethodInsn(Opcodes.INVOKEVIRTUAL, self, "submit", '(' + iDesc + ')' + RESULT_SET_DESCRIPTOR,
					false);
			query.visitVarInsn(Opcodes.ASTORE, 2);

			// Create an array list then store at 3. Generics not required.
			query.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
			query.visitInsn(Opcodes.DUP);
			query.visitVarInsn(Opcodes.ALOAD, 2);
			query.visitMethodInsn(Opcodes.INVOKEINTERFACE, RESULT_SET_TYPE, "getFetchSize", "()I", true);
			query.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "(I)V", false);
			query.visitVarInsn(Opcodes.ASTORE, 3);

			// Setup loop
			Label loop = new Label(), end = new Label();
			query.visitLabel(loop);
			query.visitFrame(Opcodes.F_SAME, 0, new Object[0], 0, new Object[0]);
			query.visitVarInsn(Opcodes.ALOAD, 2);
			query.visitMethodInsn(Opcodes.INVOKEINTERFACE, RESULT_SET_TYPE, "next", "()Z", true);
			query.visitJumpInsn(Opcodes.IFEQ, end);

			// Loop
			query.visitVarInsn(Opcodes.ALOAD, 3);

			{ // Writes the selections from the tables.
				sqlQuery.append("select ");
				var annots = proxy.getParameterAnnotations();
				var params = proxy.getParameterTypes();
				for (int a = 0, l = params.length; a < l; a++) {
					Value value = null;
					for (var b : annots[a]) {
						if (b instanceof Value v) {
							value = v;
							break;
						}
					}
					if (value == null) {
						throw new IllegalArgumentException(proxy + " does not contain Value annotation on parameter "
								+ i + ": param: " + params[a] + ", annotations: " + Arrays.toString(annots[a]));
					}

					appendQuery(sqlQuery, value.table(), value.value());

					query.visitVarInsn(Opcodes.ALOAD, 2);
					int stack = a + 1;
					if (stack <= 5) {
						// Use the single-instruction opcodes where applicable.
						query.visitInsn(Opcodes.ICONST_0 + stack);
					} else {
						query.visitIntInsn(Opcodes.BIPUSH, stack);
					}

					var clazz = params[a];
					var mapper = ClassMap.findMapper(clazz);
					if (mapper.passClass) {
						var type = Type.getType(clazz);
						query.visitLdcInsn(type);
						query.visitMethodInsn(Opcodes.INVOKEINTERFACE, RESULT_SET_TYPE, mapper.getter,
								"(ILjava/lang/Class;)Ljava/lang/Object;", true);
						query.visitTypeInsn(Opcodes.CHECKCAST, type.getInternalName());
					} else {
						query.visitMethodInsn(Opcodes.INVOKEINTERFACE, RESULT_SET_TYPE, mapper.getter,
								"(I)" + mapper.internal.descriptorString(), true);
					}
				}
				sqlQuery.setLength(sqlQuery.length() - 1);
			}

			{ // Writes the from tables
				for (Table table : tables) {
					if (table.table() == 0) {
						sqlQuery.append(" from ").append(table.value()).append(' ');
					} else {
						var match = table.match();
						char assigned = (char) ('`' + table.table());
						sqlQuery.append("left outer join ").append(table.value()).append(' ').append(assigned)
								.append(" on(").append(match.primary()).append('=').append(assigned).append('.')
								.append(match.secondary()).append(')');
					}
				}
			}

			query.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(proxy.getDeclaringClass()),
					proxy.getName(), Type.getMethodDescriptor(proxy), false);
			query.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
			query.visitInsn(Opcodes.POP);
			query.visitJumpInsn(Opcodes.GOTO, loop);

			// End loop & method, completes
			query.visitLabel(end);
			query.visitFrame(Opcodes.F_SAME, 0, new Object[0], 0, new Object[0]);
			query.visitVarInsn(Opcodes.ALOAD, 1);
			query.visitVarInsn(Opcodes.ALOAD, 3);
			query.visitMethodInsn(Opcodes.INVOKEVIRTUAL, iName, "complete", "(Ljava/lang/Object;)V", false);
			query.visitInsn(Opcodes.RETURN);
			query.visitMaxs(0, 0);
			query.visitEnd();
		}
		{ // internal helper 'submit'
			var submit = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "submit",
					Type.getMethodDescriptor(Type.getType(ResultSet.class), iType), null, null);
			submit.visitVarInsn(Opcodes.ALOAD, 0);
			submit.visitFieldInsn(Opcodes.GETFIELD, self, "statement", STATEMENT_DESCRIPTOR);
			submit.visitVarInsn(Opcodes.ASTORE, 2);

			// JVM is a stack machine; only the amount of params necessary will be taken
			// from the stack.
			// Writes the query. This also writes the query instructions.
			C0 c0 = new C0(iClass, sqlImpl.getClass());
			{
				Query[] classQueries = proxy.getDeclaringClass().getAnnotationsByType(Query.class),
						proxyQueries = proxy.getAnnotationsByType(Query.class),
						totalQueries = new Query[classQueries.length + proxyQueries.length];
				System.arraycopy(classQueries, 0, totalQueries, 0, classQueries.length);
				System.arraycopy(proxyQueries, 0, totalQueries, classQueries.length, proxyQueries.length);
				boolean ran = false;
				for (var q : totalQueries) {
					var check = i.flags() & q.mask();
					if (check != (q.maskRq() == -1 ? q.mask() : q.maskRq())) {
						continue;
					}
					if (!ran) {
						ran = true;
						sqlQuery.append("where ");
					} else {
						sqlQuery.append(" and ");
					}
					sqlQuery.append(q.query());

					for (var v : q.values()) {
						try {
							c0.compile(submit, v);
						} catch (Throwable roe) {
							throw new DatabaseException(roe, v, q, c0, i, sqlQuery, sqlImpl);
						}
					}
				}
			}
			{
				Pagination pagination = proxy.getAnnotation(Pagination.class);
				var sort = pagination.sort();
				var sortValue = sort.value();
				if (sortValue.length != 0) {
					appendQuery(sqlQuery.append(" order by "), sort.table(), sortValue);
					var l = sqlQuery.length();
					sqlQuery.replace(l - 1, l, " desc");
				}
				var limit = pagination.limit();
				if (!limit.isBlank()) {
					sqlQuery.append(" limit ?");
					try {
						c0.compile(submit, limit);
					} catch (Throwable roe) {
						throw new DatabaseException(roe, limit, pagination, sqlQuery, sqlImpl);
					}
				}
				var offset = pagination.offset();
				if (!offset.isBlank()) {
					sqlQuery.append(" offset ?");
					try {
						c0.compile(submit, offset);
					} catch (Throwable roe) {
						throw new DatabaseException(roe, offset, pagination, sqlQuery, sqlImpl);
					}
				}
			}

			submit.visitVarInsn(Opcodes.ALOAD, 2);
			submit.visitMethodInsn(Opcodes.INVOKEINTERFACE, STATEMENT_TYPE, "executeQuery",
					"()" + RESULT_SET_DESCRIPTOR, true);
			submit.visitInsn(Opcodes.ARETURN);
			submit.visitMaxs(0, 0);
			submit.visitEnd();
		}
		{ // constructor
			var init = writer.visitMethod(0, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, provider), null, null);
			init.visitVarInsn(Opcodes.ALOAD, 0);
			init.visitVarInsn(Opcodes.ALOAD, 1);
			init.visitLdcInsn(sqlQuery.toString());
			// Cache type has to be passed else it is impossible to initialise the class
			// otherwise.
			init.visitMethodInsn(Opcodes.INVOKESPECIAL, statementHandler, "<init>",
					Type.getMethodDescriptor(Type.VOID_TYPE, provider, STRING_TYPE), false);
			init.visitInsn(Opcodes.RETURN);
			init.visitMaxs(3, 1);
			init.visitEnd();
		}
		writer.visitEnd();
		array = writer.toByteArray();
		try {
			// Allow for trivial debugging and decompilation of the handler if it severely
			// broke.
			if (Boolean.parseBoolean(System.getProperty("statement.cache.dump"))) {
				try (var out = new FileOutputStream("scasm-dump-" + System.nanoTime() + ".class")) {
					out.write(array);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			// Ensures that the statement handler is entirely initialised before going on to
			// the hidden class.
			SELF.ensureInitialized(StatementHandler.class);
			var nest = SELF.defineHiddenClass(array, true);
			var cons = nest.findConstructor(nest.lookupClass(),
					MethodType.methodType(void.class, SqlConnectionProvider.class));
			return (StatementHandler) cons.invoke(sqlImpl);
		} catch (VirtualMachineError | LinkageError | IllegalAccessException | IllegalArgumentException
				| NoSuchMethodException | SecurityException | InstantiationException | InvocationTargetException
				| NullPointerException | SQLException | DatabaseException roe) {
			Throwable suppressed = null;
			try (var out = new FileOutputStream("scasm.class")) {
				out.write(array);
			} catch (Exception e) {
				suppressed = e;
			}
			var pe = new DatabaseException(roe, i, sqlQuery, "Bytecode has been dumped at scasm.class.");
			if (suppressed != null) {
				pe.addSuppressed(suppressed);
			}
			if (roe instanceof VirtualMachineError) {
				throw new Error("Virtual machine has failed.", pe);
			}
			throw pe;
		} catch (Throwable throwable) {
			throw new AssertionError("This should never occur.", throwable);
		}
	}

	private static void appendQuery(StringBuilder sqlQuery, int table, String[] nameStack) {
		int c = sqlQuery.length();
		if (table == 0) {
			sqlQuery.append(nameStack[0]);
		} else {
			sqlQuery.append((char) ('`' + table)).append('.').append(nameStack[0]);
		}
		for (int v = 1; v < nameStack.length; v++) {
			sqlQuery.insert(c, '(').append(").").append(nameStack[v]);
		}
		sqlQuery.append(',');
	}

	private static class HandlerEntry extends SoftReference<StatementHandler> implements AutoCloseable {
		private final int flags;
		private Statement statement;

		public HandlerEntry(int flags, StatementHandler referent, ReferenceQueue<StatementHandler> queue)
				throws DatabaseException {
			super(referent, queue);
			this.flags = flags;
			prepare();
		}

		private void prepare() throws DatabaseException {
			var self = get();
			if (self != null) {
				this.statement = self.prepareStatement();
			}
		}

		@Override
		public void close() throws DatabaseException {
			try {
				var self = get();
				if (self != null) {
					self.closeStatement();
				} else {
					statement.close();
				}
			} catch (SQLException sql) {
				throw new DatabaseException(sql, statement);
			} finally {
				statement = null;
			}
		}
	}
}
