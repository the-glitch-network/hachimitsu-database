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

package net.kjp12.hachimitsu.database.impl;// Created 2021-22-06T05:23:53

import net.kjp12.hachimitsu.database.api.SqlConnectionProvider;
import net.kjp12.hachimitsu.database.api.StatementCache;
import net.kjp12.hachimitsu.database.api.annotation.Query;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * SQL Submitter Compiler. Expects a certain structure, do not attempt to use
 * directly.
 * <p>
 * <h3>Tokens</h3>
 * 
 * <pre>
 * <b>&lt;  </b> L*.0 &gt; 1, *LOAD  \d + 4<sup><a
 * href="#class-ref1">[1]</a></sup>
 * <b>&gt;  </b> L*.0 &gt; 2, *STORE \d + 4<sup><a
 * href="#class-ref1">[1]</a></sup>
 * <b>?  </b> L*.1 &gt; 0, IFNUL<sup><a href="#class-ref2">[2]</a></sup>
 * <b>^  </b> L*.0 &gt; 0, ALOAD  3<sup><a href="#class-ref3">[3]</a></sup>
 * <b>(  </b> L*.0 &gt; 0, PUSH   L2
 * <b>)  </b> L2.0 &gt; 0, POP    L2
 * <b>,  </b> L1.0 &gt; 0, POP    L1
 * <b>.  </b> L*.* &gt; 0, DREF   CTX
 * <b>NUL</b> L*.* &gt;  , POP    *<sup><a href="#class-ref4">[4]</a></sup>
 * </pre>
 * <ul>
 * <li id="class-ref1"><b>1</b> - +4 offset is required for variables. The
 * selected opcode is dependent on the variable at call time.</li>
 * <li id="class-ref2"><b>2</b> - C0 compilation intrinsic candidate.</li>
 * <li id="class-ref3"><b>3</b> - This loads the provider and immediately casts
 * it to the applicable class.</li>
 * <li id="class-ref4"><b>4</b> - End of File, must pop or crash. Must be
 * allowed under all contexts.</li>
 * </ul>
 * <p>
 * <h3>Contexts</h3>
 * <ul>
 * <li>Allowed off of <code>L*.0</code> = <code>&lt;&gt;^.(</code></li>
 * <li>Allowed off of <code>L*.1</code> = <code>?.</code></li>
 * <li>Allowed off of <code>L*.2</code> = <code>.</code></li>
 * <li>Allowed off of <code>L1.0</code> = <code>&lt;&gt;^.,()</code></li>
 * </ul>
 * <p>
 * <h3>Code compilation</h3> <code>&lt;0?^.worldIndex(causeWorld)&gt;0</code>
 *
 * <h4>First encounter of both variable 0 and carrot</h4>
 * <code>..., var4 = (var3 = (Carrier) this.provider).worldIndex(var1.causeWorld), ...</code>
 * 
 * <pre>
 * ALOAD         0
 * GETFIELD      impl/Handler provider    Lapi/Provider;
 * CHECKCAST     your/Carrier
 * DUP
 * ASTORE        3
 * ALOAD         1
 * GETFIELD      your/Record  causeWorld  Lyour/World;
 * INVOKEVIRTUAL your/Carrier worldIndex  (Lyour/World;)I
 * DUP
 * ISTORE        4
 * </pre>
 *
 * <h4>Carrot already initialised</h4>
 * <code>..., var4 = var3.worldIndex(var1.causeWorld), ...</code>
 * 
 * <pre>
 * ALOAD         3
 * ALOAD         1
 * GETFIELD      your/Record  causeWorld  Lyour/World;
 * INVOKEVIRTUAL your/Carrier worldIndex  (Lyour/World;)I
 * DUP
 * ISTORE        4
 * </pre>
 *
 * <h4>Variable 4 already initialised</h4> <code>..., var4, ...</code>
 * 
 * <pre>
 * ILOAD         4
 * </pre>
 *
 * @author KJP12
 * @since ${version}
 * @see ClassMap
 * @see StatementCache
 * @see Query#values()
 **/
public class C0 implements Opcodes {
	private static final char[] T_NONE = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, '^', 0, 0, 0, 0, 0, 0, 0, 0, 0, '(', ')', 0, 0, ',', 0, '.', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, '<', 0, '>', 0 }, // StringUtils.createCharHashArray("<>^.,()\0"),
			T_LOAD = { 0, ')', 0, 0, ',', 0, '.', '?' }, // StringUtils.createCharHashArray("?.,)\0"),
			T_STORE = { 0, ')', 0, 0, ',', 0, '.', 0 }; // StringUtils.createCharHashArray(".,)\0");

	private static final String statementHandler = Type.getInternalName(StatementHandler.class),
			providerName = Type.getInternalName(SqlConnectionProvider.class);

	private static final int C_NONE = 0, C_LOAD = 1, C_STORE = 2;

	private int ia, ib, is;
	private String value;

	int index = 0;
	boolean carrot = false;
	Class<?>[] locals = new Class<?>[0];
	Class<?> fallback;
	Class<? extends SqlConnectionProvider> sqlImpl;

	public C0(Class<?> fallback, Class<? extends SqlConnectionProvider> sqlImpl) {
		this.fallback = fallback;
		this.sqlImpl = sqlImpl;
	}

	public void compile(MethodVisitor submit, String value) throws NoSuchFieldException, NoSuchMethodException {
		// Load statement into stack.
		submit.visitVarInsn(Opcodes.ALOAD, 2);
		// Load the index onto the stack.
		if (++index <= 5) {
			submit.visitInsn(Opcodes.ICONST_0 + index);
		} else {
			submit.visitIntInsn(Opcodes.BIPUSH, index);
		}

		this.value = value;
		ia = ib = -1;
		is = C_NONE;
		var mapper = ClassMap.findMapper(l1(submit, false));
		submit.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/sql/PreparedStatement", mapper.setter,
				"(I" + (mapper == ClassMap.VOID ? "Ljava/lang/Object;" : mapper.internal.descriptorString()) + ")V",
				true);
	}

	/**
	 * Linear single-return compiler
	 *
	 * @param submit The visitor
	 * @param nested If this is nested within a method call.
	 * @return The last class context. May be {@link Void#TYPE void.class}.
	 */
	private Class<?> l1(MethodVisitor submit, boolean nested) throws NoSuchMethodException, NoSuchFieldException {
		Class<?> context = void.class;
		while (step()) {
			char e = ib >= value.length() ? '\0' : value.charAt(ib);
			boolean dropL1 = e == ')' || e == ',';
			if (!nested && dropL1) {
				throw new IllegalArgumentException(value + " @ " + ib + " `" + e + "` not valid for unnested. " + this);
			}
			switch (is) {
				case C_NONE -> {
					switch (e) {
						case '<' -> is = C_LOAD;
						case '>' -> is = C_STORE;
						case '^' -> {
							lc(submit);
							context = sqlImpl;
						}
						case '.', ',', ')', '\0' -> {
							if (ib - ia > 1) {
								if (context == void.class) {
									submit.visitVarInsn(Opcodes.ALOAD, 1);
									context = fallback;
								}
								context = vf(submit, context.getField(value.substring(ia, ib)));
							}
						}
						case '(' -> {
							if (ib - ia <= 1) {
								throw new IllegalStateException(this.toString());
							}
							if (context == void.class) {
								submit.visitVarInsn(Opcodes.ALOAD, 1);
								context = fallback;
							}
							var name = value.substring(ia, ib);
							var params = l2(submit);
							var m = Arrays.stream(context.getMethods()).filter(method -> method.getName().equals(name)
									&& method.getParameterCount() == params.length).filter(method -> {
										var other = method.getParameterTypes();
										for (int i = 0; i < other.length; i++) {
											if (!other[i].isAssignableFrom(params[i])) {
												return false;
											}
										}
										return true;
									}).findFirst().get();
							context = vm(submit, m);
						}
					}
				}
				case C_LOAD -> {
					int v = Integer.parseInt(value.substring(ia, ib));
					switch (e) {
						case '?' -> {
							var clazz = getLocal(v);
							if (clazz != null) {
								submit.visitVarInsn(ClassMap.findMapper(clazz).load, v + 4);
								// TODO: does not support method calls
								var ne = value.indexOf(';');
								ib = ne == -1 ? value.length() : ne;
								return clazz;
							}
						}
						case '.', ',', ')', '\0' -> {
							context = getLocal(v);
							if (context == null) {
								throw new IllegalStateException("local " + v + " not stored " + this);
							}
							submit.visitVarInsn(ClassMap.findMapper(context).load, v + 4);
						}
					}
					is = C_NONE;
				}
				case C_STORE -> {
					if (context == void.class) {
						throw new IllegalStateException("attempted store on void " + this);
					}
					int v = Integer.parseInt(value.substring(ia, ib));
					// Originally matches `.`, `,`, `)`
					setLocal(v, context);
					submit.visitInsn(Opcodes.DUP);
					submit.visitVarInsn(ClassMap.findMapper(context).store, v + 4);
					is = C_NONE;
				}
			}
			if (dropL1) {
				is = C_NONE;
				return context;
			}
		}
		return context;
	}

	/**
	 * Multi-return Parameter compiler.
	 *
	 * @param submit The visitor
	 * @return Array of classes for parameters.
	 */
	private Class<?>[] l2(MethodVisitor submit) throws NoSuchFieldException, NoSuchMethodException {
		var list = new ArrayList<Class<?>>();
		while (ib < value.length() && value.charAt(ib) != ')') {
			var clazz = l1(submit, true);
			if (clazz != void.class) {
				list.add(clazz);
			}
		}
		return list.toArray(new Class<?>[0]);
	}

	private Class<?> getLocal(int i) {
		return i < locals.length ? locals[i] : null;
	}

	private void setLocal(int i, Class<?> clazz) {
		if (i >= locals.length) {
			locals = Arrays.copyOf(locals, i + 1);
		}
		locals[i] = clazz;
	}

	// private int p0(String value, int ia, int il) {
	// int s = 1;
	// while(s > 0) {
	// ia = StringUtils.seekToDelimiter(value, method, il, ia);
	// s += (value.charAt(ia) == '(' ? 1 : -1);
	// if(++ia >= il) break;
	// }
	// if(s != 0) throw new IllegalArgumentException("Cannot parse " + value + ": "
	// + ia + " -> " + il + ": uncapped parameters; current stack = " + s);
	// return ia - 1;
	// }

	/**
	 * Load carrot into the visitor.
	 * <p>
	 * If this is the first carrot visit, bytecode will be emitted to get the
	 * provider, cast it and store it at variable 3.
	 *
	 * @param submit The visitor.
	 */
	private void lc(MethodVisitor submit) {
		if (carrot) {
			submit.visitVarInsn(Opcodes.ALOAD, 3);
		} else {
			carrot = true;
			submit.visitVarInsn(Opcodes.ALOAD, 0);
			submit.visitFieldInsn(Opcodes.GETFIELD, statementHandler, "provider", 'L' + providerName + ';');
			submit.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(sqlImpl));
			submit.visitInsn(Opcodes.DUP);
			submit.visitVarInsn(Opcodes.ASTORE, 3);
		}
	}

	/**
	 * Visits method using parameters obtained from reflective access.
	 *
	 * @param submit The visitor
	 * @param method The method to convert to a raw ASM call.
	 * @return The return type of the method. May be {@link Void#TYPE void.class}.
	 */
	private Class<?> vm(MethodVisitor submit, Method method) {
		var mods = method.getModifiers();
		var dec = method.getDeclaringClass();
		submit.visitMethodInsn(
				(mods & Opcodes.ACC_STATIC) != 0 ? Opcodes.INVOKESTATIC
						: (mods & Opcodes.ACC_ABSTRACT) != 0 ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL,
				Type.getInternalName(dec), method.getName(), Type.getMethodDescriptor(method),
				(dec.getModifiers() & Opcodes.ACC_INTERFACE) != 0);
		return method.getReturnType();
	}

	/**
	 * Visit field using parameters obtained from reflective access.
	 *
	 * @param submit The visitor
	 * @param field  The field to convert to a raw ASM call.
	 * @return The return type of the field. Cannot ever be null or void.
	 */
	private Class<?> vf(MethodVisitor submit, Field field) {
		var ret = field.getType();
		submit.visitFieldInsn((field.getModifiers() & Opcodes.ACC_STATIC) != 0 ? Opcodes.GETSTATIC : Opcodes.GETFIELD,
				Type.getInternalName(field.getDeclaringClass()), field.getName(), ret.descriptorString());
		return ret;
	}

	private boolean step() {
		if (ib >= value.length()) {
			return false;
		}
		ib = seekToDelimiter(value, switch (is) {
			case C_NONE -> T_NONE;
			case C_LOAD -> T_LOAD;
			case C_STORE -> T_STORE;
			default -> throw new IllegalStateException(is + " is an invalid state; expected 0 - 3");
		}, value.length(), ia = ib + 1);
		return true;
	}

	@Override
	public String toString() {
		return "C0{" + "ia=" + ia + ", ib=" + ib + ", is=" + is + ", value='" + value + '\'' + ", index=" + index
				+ ", carrot=" + carrot + ", locals=" + Arrays.toString(locals) + ", fallback=" + fallback + ", sqlImpl="
				+ sqlImpl + '}';
	}

	private static int seekToDelimiter(final String toSplit, final char[] delimiters, final int lim, int ib) {
		final int mask = delimiters.length - 1;
		char c;
		while (ib < lim && delimiters[(c = toSplit.charAt(ib)) & mask] != c) {
			ib++;
		}
		return ib;
	}
}
