package io.github.dmlloyd.autolinker;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import io.github.dmlloyd.classfile.ClassBuilder;
import io.github.dmlloyd.classfile.ClassFile;
import io.github.dmlloyd.classfile.CodeBuilder;
import io.github.dmlloyd.classfile.Label;
import io.github.dmlloyd.classfile.TypeKind;
import io.github.dmlloyd.classfile.extras.reflect.AccessFlag;
import io.smallrye.common.constraint.Assert;
import io.smallrye.common.cpu.CPU;
import io.smallrye.common.os.OS;

/**
 * A native method auto-linker.
 */
public final class AutoLinker {
    private final MethodHandles.Lookup lookup;
    private final ClassValue<Object> linkables = new ClassValue<Object>() {
        protected Object computeValue(final Class<?> type) {
            String packageName = type.getPackageName();
            String simpleName = type.getSimpleName();
            byte[] bytes = compileAutoLinkerFor(type, ClassDesc.of(packageName, simpleName + "$$AutoLinker"));
            try {
                MethodHandles.Lookup definedLookup = lookup.defineHiddenClass(bytes, true);
                MethodHandle ctor = definedLookup.findConstructor(definedLookup.lookupClass(), MethodType.methodType(void.class));
                return ctor.invoke();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (NoSuchMethodException e) {
                NoSuchMethodError e2 = new NoSuchMethodError(e.getMessage());
                e2.setStackTrace(e.getStackTrace());
                throw e2;
            } catch (IllegalAccessException e) {
                IllegalAccessError e2 = new IllegalAccessError(e.getMessage());
                e2.setStackTrace(e.getStackTrace());
                throw e2;
            } catch (Throwable t) {
                throw new UndeclaredThrowableException(t);
            }
        }
    };

    /**
     * Construct a new instance.
     *
     * @param lookup the lookup to use for resolution (must not be {@code null})
     */
    public AutoLinker(final MethodHandles.Lookup lookup) {
        this.lookup = Assert.checkNotNullParam("lookup", lookup);
    }

    /**
     * Autolink the given interface.
     * Each method on the interface which is annotated with {@link Link}
     * will be implemented to automatically link against the corresponding native function.
     * If the interface fails to be linked, this method will throw a run time exception.
     *
     * @param interface_ the interface (must not be {@code null})
     * @return the autolinked implementation of the interface (not {@code null})
     * @param <T> the type of the interface
     */
    public <T> T autoLink(Class<? extends T> interface_) {
        return interface_.cast(linkables.get(interface_));
    }

    /**
     * Compile an auto-linker class for the given type.
     * This can be used to generate an offline linker class.
     *
     * @param interface_ the interface (must not be {@code null})
     * @param classDesc the descriptor of the class to generate (must not be {@code null})
     * @return the class bytes
     */
    public static byte[] compileAutoLinkerFor(final Class<?> interface_, final ClassDesc classDesc) {
        if (! interface_.isInterface()) {
            throw new IllegalArgumentException(interface_ + " is not an interface");
        }
        HashSet<Class<?>> visitedInterfaces = new HashSet<>();
        ArrayDeque<Class<?>> breadthQueue = new ArrayDeque<>();
        breadthQueue.add(interface_);
        populateQueue(interface_, breadthQueue, visitedInterfaces);
        ClassFile cf = ClassFile.of(ClassFile.StackMapsOption.GENERATE_STACK_MAPS);
        return cf.build(classDesc, zb -> {
            zb.withFlags(AccessFlag.FINAL);
            zb.withVersion(ClassFile.JAVA_22_VERSION, 0);
            zb.withInterfaceSymbols(interface_.describeConstable().orElseThrow());
            // create trivial constructor
            zb.withMethod("<init>", MTD_void, ClassFile.ACC_PUBLIC, mb -> {
                mb.withCode(cb -> {
                    cb.aload(0);
                    cb.invokespecial(ConstantDescs.CD_Object, "<init>", MTD_void);
                    cb.return_();
                });
            });
            HashMap<String, HashSet<MethodType>> visitedMethods = new HashMap<>();
            for (Class<?> current = breadthQueue.pollFirst(); current != null; current = breadthQueue.pollFirst()) {
                processMethods(zb, classDesc, current, visitedMethods);
            }
        });
    }

    private static void populateQueue(final Class<?> type, final ArrayDeque<Class<?>> breadthQueue, final HashSet<Class<?>> visitedInterfaces) {
        Class<?>[] interfaces = type.getInterfaces();
        List<Class<?>> filteredSupers = new ArrayList<>(interfaces.length);
        for (Class<?> superInterface : interfaces) {
            if (visitedInterfaces.add(superInterface)) {
                filteredSupers.add(superInterface);
            }
        }
        breadthQueue.addAll(filteredSupers);
        for (Class<?> filteredSuper : filteredSupers) {
            populateQueue(filteredSuper, breadthQueue, visitedInterfaces);
        }
    }

    private static void processMethods(final ClassBuilder zb, final ClassDesc classDesc, final Class<?> interface_, final HashMap<String, HashSet<MethodType>> visitedMethods) {
        for (Method method : interface_.getDeclaredMethods()) {
            int mods = method.getModifiers();
            if (Modifier.isStatic(mods)) {
                continue;
            }
            Link link = method.getAnnotation(Link.class);
            if (link == null) {
                // just don't implement it
                continue;
            }
            MethodType type = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
            if (visitedMethods.computeIfAbsent(method.getName(), AutoLinker::newHashSet).add(type)) {
                if (! Modifier.isAbstract(mods)) {
                    // exclude method on all levels
                    continue;
                }
                List<Transformation> transformations = new ArrayList<>(method.getParameterCount() + 4);
                Parameter[] parameters = method.getParameters();
                for (final Parameter parameter : parameters) {
                    if (parameter.getAnnotation(Link.va_start.class) != null) {
                        transformations.add(Transformation.START_VA);
                    }
                    if (parameter.getAnnotation(Link.capture.class) != null) {
                        transformations.add(Transformation.CAPTURE);
                        continue;
                    }
                    Link.as linkAs = parameter.getAnnotation(Link.as.class);
                    if (linkAs != null) {
                        transformations.add(transformationFor(linkAs.value()));
                    } else {
                        // determine type
                        transformations.add(Transformation.forJavaType(parameter.getType()));
                    }
                }
                Transformation returnTransformation;
                Link.as returnLinkAs = method.getAnnotation(Link.as.class);
                if (returnLinkAs != null) {
                    returnTransformation = transformationFor(returnLinkAs.value());
                } else {
                    returnTransformation = Transformation.forJavaType(method.getReturnType());
                }
                MethodTypeDesc downcallType = MethodTypeDesc.of(
                    returnTransformation.carrier().describeConstable().orElseThrow(),
                    transformations.stream().map(Transformation::carrier).filter(c -> c != void.class).map(Class::describeConstable).map(Optional::orElseThrow).toArray(ClassDesc[]::new)
                );
                Link.critical critical = method.getAnnotation(Link.critical.class);

                // add the bootstrap for the indy
                int hash = type.hashCode();
                String linkName = method.getName() + "$$link_" + Integer.toHexString(hash);
                zb.withMethod(linkName, MTD_link, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_SYNTHETIC, mb -> {
                    mb.withCode(cb -> {
                        // first get our linker
                        cb.invokestatic(CD_Linker, "nativeLinker", MTD_Linker, true);
                        // stack: linker
                        cb.dup();
                        // stack: linker linker
                        // now get the caller-sensitive symbol lookup
                        cb.invokestatic(CD_SymbolLookup, "loaderLookup", MTD_SymbolLookup, true);
                        // stack: linker linker loaderLookup
                        cb.swap();
                        // stack: linker loaderLookup linker
                        // now configure it to fall back to the default lookup
                        cb.invokeinterface(CD_Linker, "defaultLookup", MTD_SymbolLookup);
                        // stack: linker loaderLookup defaultLookup
                        cb.invokeinterface(CD_SymbolLookup, "or", MTD_SymbolLookup_SymbolLookup);
                        // stack: linker combinedLookup
                        // now look up our symbol
                        cb.aload(1);
                        // stack: linker combinedLookup name
                        cb.invokeinterface(CD_SymbolLookup, "find", MTD_Optional_String);
                        // stack: linker optional
                        cb.dup();
                        // stack: linker optional optional
                        cb.invokevirtual(CD_Optional, "isPresent", MTD_boolean);
                        // stack: linker optional boolean
                        cb.ifThen(tb -> {
                            // stack: linker optional
                            tb.invokevirtual(CD_Optional, "get", MTD_Object);
                            tb.checkcast(CD_MemorySegment);
                            // stack: linker fnPtr
                            // get the return type, if any
                            boolean nonVoid = returnTransformation != Transformation.VOID;
                            if (nonVoid) {
                                returnTransformation.emitLayout(tb);
                            }
                            // get the function descriptor
                            int layoutCnt = (int) transformations.stream().filter(Transformation::hasLayout).count();
                            pushInt(tb, layoutCnt);
                            tb.anewarray(CD_MemoryLayout);
                            int idx = 0;
                            for (Transformation transformation : transformations) {
                                if (transformation.hasLayout()) {
                                    tb.dup();
                                    pushInt(tb, idx ++);
                                    transformation.emitLayout(tb);
                                    tb.aastore();
                                }
                            }
                            if (nonVoid) {
                                tb.invokestatic(CD_FunctionDescriptor, "of", MTD_FunctionDescriptor_MemoryLayout_MemoryLayout_array, true);
                            } else {
                                tb.invokestatic(CD_FunctionDescriptor, "ofVoid", MTD_FunctionDescriptor_MemoryLayout_array, true);
                            }
                            // stack: linker fnPtr descriptor
                            // now we just need the options
                            int optCnt = (critical != null ? 1 : 0) + (int) transformations.stream().filter(Transformation::hasOption).count();
                            pushInt(tb, optCnt);
                            tb.anewarray(CD_Linker_Option);
                            idx = 0;
                            int argIdx = 0;
                            for (Transformation transformation : transformations) {
                                if (transformation.hasOption()) {
                                    tb.dup();
                                    pushInt(tb, idx ++);
                                    transformation.applyOption(tb, argIdx, parameters[argIdx]);
                                    tb.aastore();
                                }
                                if (transformation.consumeArgument()) {
                                    argIdx++;
                                }
                            }
                            if (critical != null) {
                                tb.dup();
                                pushInt(tb, idx);
                                if (critical.heap()) {
                                    tb.iconst_1();
                                } else {
                                    tb.iconst_0();
                                }
                                tb.invokestatic(CD_Linker_Option, "critical", MTD_Linker_Option_boolean, true);
                                tb.aastore();
                            }
                            // stack: linker fnPtr descriptor options
                            // finally link the function
                            tb.invokeinterface(CD_Linker, "downcallHandle", MTD_MethodHandle_MemorySegment_FunctionDescriptor_Linker_Option_array);
                            // stack: handle
                            tb.aload(2);
                            // stack: handle exp_type
                            tb.invokevirtual(ConstantDescs.CD_MethodHandle, "asType", MTD_MethodHandle_MethodType);
                            // now make a constant call site for it
                            tb.new_(CD_ConstantCallSite);
                            // stack: handle ccs
                            tb.dup_x1();
                            // stack: ccs handle ccs
                            tb.swap();
                            // stack: ccs ccs handle
                            tb.invokespecial(CD_ConstantCallSite, "<init>", MTD_void_MethodHandle);
                            // stack: ccs
                            tb.areturn();
                            // stack: -- (done)
                        });

                        // otherwise, linkage has failed
                        // stack: linker optional
                        cb.pop();
                        cb.pop();
                        // stack: --
                        cb.new_(CD_UnsatisfiedLinkError);
                        // stack: ule
                        cb.dup();
                        // stack: ule ule
                        cb.invokespecial(CD_UnsatisfiedLinkError, "<init>", MTD_void);
                        // stack: ule
                        cb.athrow();
                        // stack: -- (done)
                    });
                });
                // add the method
                zb.withMethod(method.getName(), type.describeConstable().orElseThrow(), ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SYNTHETIC, mb -> {
                    mb.withCode(cb -> {
                        boolean arena = false;
                        boolean closeArena = false;
                        int arenaIdx = -1;
                        // first, see if we need to set up an allocation arena
                        boolean heap = critical != null && critical.heap();
                        Iterator<Transformation> iterator = transformations.iterator();
                        int paramCnt = method.getParameterCount();
                        for (int i = 0; i < paramCnt; i++) {
                            final Class<?> argType = parameters[i].getType();
                            if (argType == LazyLink.ARENA) {
                                arena = true;
                                // just ignore extra arenas, I guess
                                if (arenaIdx == -1) {
                                    arenaIdx = cb.parameterSlot(i);
                                }
                                continue;
                            }
                            boolean isNativeEnum = NativeEnum.class.isAssignableFrom(argType);
                            while (iterator.hasNext()) {
                                final Transformation transformation = iterator.next();
                                if (transformation.needsArena(isNativeEnum ? int.class : argType, heap)) {
                                    arena = true;
                                }
                                if (transformation.consumeArgument()) {
                                    break;
                                }
                            }
                        }
                        // set up the arena, if any
                        if (arena && arenaIdx == -1) {
                            closeArena = true;
                            arenaIdx = cb.allocateLocal(TypeKind.REFERENCE);
                            cb.invokestatic(CD_Arena, "ofConfined", MTD_Arena, true);
                            cb.astore(arenaIdx);
                        }
                        Label tryRegionStart = cb.newBoundLabel();
                        ArrayDeque<Consumer<CodeBuilder>> cleanups = new ArrayDeque<>();
                        // reset and begin again
                        iterator = transformations.iterator();
                        for (int i = 0; i < paramCnt; i++) {
                            Parameter parameter = parameters[i];
                            Link.dir dirAnn = parameter.getAnnotation(Link.dir.class);
                            Direction dir = dirAnn == null ? null : dirAnn.value();
                            final Class<?> argType = parameter.getType();
                            if (argType == LazyLink.ARENA) {
                                // skip
                                continue;
                            }
                            boolean isNativeEnum = NativeEnum.class.isAssignableFrom(argType);
                            int ne = -1;
                            int paramSlot = cb.parameterSlot(i);
                            if (isNativeEnum) {
                                cb.aload(paramSlot);
                                if (argType.isInterface()) {
                                    cb.invokeinterface(argType.describeConstable().orElseThrow(), "nativeCode", MTD_int);
                                } else {
                                    cb.invokevirtual(argType.describeConstable().orElseThrow(), "nativeCode", MTD_int);
                                }
                                ne = cb.allocateLocal(TypeKind.INT);
                                cb.istore(ne);
                            }
                            while (iterator.hasNext()) {
                                final Transformation transformation = iterator.next();
                                Consumer<CodeBuilder> cleanup;
                                if (isNativeEnum) {
                                    cleanup = transformation.applyArgument(cb, ne, int.class, heap, arenaIdx, dir);
                                } else {
                                    cleanup = transformation.applyArgument(cb, paramSlot, argType, heap, arenaIdx, dir);
                                }
                                if (cleanup != null) {
                                    cleanups.addLast(cleanup);
                                }
                                if (transformation.consumeArgument()) {
                                    break;
                                }
                            }
                        }

                        String altName = link.name();
                        String fnName = altName != null && ! altName.isEmpty() ? altName : method.getName();
                        cb.invokedynamic(DynamicCallSiteDesc.of(
                            MethodHandleDesc.ofMethod(
                                DirectMethodHandleDesc.Kind.STATIC,
                                classDesc,
                                linkName,
                                MTD_link
                            ),
                            fnName,
                            downcallType
                        ));
                        // apply all cleanups
                        while (! cleanups.isEmpty()) {
                            cleanups.removeLast().accept(cb);
                        }
                        Class<?> returnType = method.getReturnType();
                        if (NativeEnum.class.isAssignableFrom(returnType)) {
                            returnTransformation.emitReturn(cb, int.class);
                            cb.invokestatic(returnType.describeConstable().orElseThrow(), "fromNativeCode", MethodTypeDesc.of(returnType.describeConstable().orElseThrow(), ConstantDescs.CD_int), returnType.isInterface());
                        } else {
                            returnTransformation.emitReturn(cb, returnType);
                        }
                        if (closeArena) {
                            Label tryRegionEnd = cb.newBoundLabel();
                            Label catcher = cb.newLabel();
                            cb.exceptionCatch(tryRegionStart, tryRegionEnd, catcher, Optional.empty());
                            // clean up arena
                            cb.aload(arenaIdx);
                            cb.invokeinterface(CD_Arena, "close", MTD_void);
                            cb.return_(TypeKind.from(returnType));
                            cb.labelBinding(catcher);
                            // clean up arena (catch)
                            cb.aload(arenaIdx);
                            cb.invokeinterface(CD_Arena, "close", MTD_void);
                            // rethrow the exception
                            cb.athrow();
                        } else {
                            cb.return_(TypeKind.from(returnType));
                        }
                    });
                });
            }
        }
    }

    static void pushInt(CodeBuilder cb, int val) {
        switch (val) {
            case -1 -> cb.iconst_m1();
            case 0 -> cb.iconst_0();
            case 1 -> cb.iconst_1();
            case 2 -> cb.iconst_2();
            case 3 -> cb.iconst_3();
            case 4 -> cb.iconst_4();
            case 5 -> cb.iconst_5();
            default -> {
                if (Byte.MIN_VALUE <= val && val <= Byte.MAX_VALUE) {
                    cb.bipush(val);
                } else if (Short.MIN_VALUE <= val && val <= Short.MAX_VALUE) {
                    cb.sipush(val);
                } else {
                    int ntz = Integer.numberOfTrailingZeros(val);
                    if (ntz > 0) {
                        int shifted = val >> ntz;
                        if (Short.MIN_VALUE <= shifted && shifted <= Short.MAX_VALUE) {
                            // optimize!
                            pushInt(cb, shifted);
                            pushInt(cb, ntz);
                            cb.ishl();
                        } else {
                            cb.ldc(val);
                        }
                    } else {
                        cb.ldc(val);
                    }
                }
            }
        }
    }

    private static <T> HashSet<T> newHashSet(Object ignored) {
        return new HashSet<>();
    }

    private static Transformation transformationFor(final AsType asType) {
        return switch (asType) {
            case signed_char, int8_t, char_ -> Transformation.S8;
            case unsigned_char, char8_t, uint8_t -> Transformation.U8;
            case int_, int32_t -> Transformation.S32;
            case unsigned_int, char32_t, uint32_t -> Transformation.U32;
            case long_ -> c_long;
            case unsigned_long -> c_unsigned_long;
            case long_long, int64_t -> Transformation.S64;
            case unsigned_long_long, uint64_t -> Transformation.U64;

            case int16_t -> Transformation.S16;
            case uint16_t, char16_t -> Transformation.U16;

            case char7_t -> Transformation.U7;

            case ptrdiff_t, ssize_t, intptr_t -> c_intptr_t;
            case uintptr_t, size_t -> c_uintptr_t;

            case ptr -> Transformation.PTR;
            case void_ -> Transformation.VOID;
        };
    }

    static final ClassDesc CD_AddressLayout = ClassDesc.of("java.lang.foreign.AddressLayout");
    static final ClassDesc CD_Arena = ClassDesc.of("java.lang.foreign.Arena");
    static final ClassDesc CD_Buffer = Buffer.class.describeConstable().orElseThrow();
    static final ClassDesc CD_ByteBuffer = ByteBuffer.class.describeConstable().orElseThrow();
    static final ClassDesc CD_Charset = Charset.class.describeConstable().orElseThrow();
    static final ClassDesc CD_ConstantCallSite = ConstantCallSite.class.describeConstable().orElseThrow();
    static final ClassDesc CD_FunctionDescriptor = ClassDesc.of("java.lang.foreign.FunctionDescriptor");
    static final ClassDesc CD_Linker = ClassDesc.of("java.lang.foreign.Linker");
    static final ClassDesc CD_Linker_Option = ClassDesc.of("java.lang.foreign.Linker$Option");
    static final ClassDesc CD_Linker_Option_array = CD_Linker_Option.arrayType();
    static final ClassDesc CD_MemoryLayout = ClassDesc.of("java.lang.foreign.MemoryLayout");
    static final ClassDesc CD_MemorySegment = ClassDesc.of("java.lang.foreign.MemorySegment");
    static final ClassDesc CD_Optional = Optional.class.describeConstable().orElseThrow();
    static final ClassDesc CD_SegmentAllocator = ClassDesc.of("java.lang.foreign.SegmentAllocator");
    static final ClassDesc CD_StandardCharsets = StandardCharsets.class.describeConstable().orElseThrow();
    static final ClassDesc CD_SymbolLookup = ClassDesc.of("java.lang.foreign.SymbolLookup");
    static final ClassDesc CD_ValueLayout = ClassDesc.of("java.lang.foreign.ValueLayout");
    static final ClassDesc CD_ValueLayout_OfBoolean = ClassDesc.of("java.lang.foreign.ValueLayout$OfBoolean");
    static final ClassDesc CD_ValueLayout_OfByte = ClassDesc.of("java.lang.foreign.ValueLayout$OfByte");
    static final ClassDesc CD_ValueLayout_OfChar = ClassDesc.of("java.lang.foreign.ValueLayout$OfChar");
    static final ClassDesc CD_ValueLayout_OfDouble = ClassDesc.of("java.lang.foreign.ValueLayout$OfDouble");
    static final ClassDesc CD_ValueLayout_OfFloat = ClassDesc.of("java.lang.foreign.ValueLayout$OfFloat");
    static final ClassDesc CD_ValueLayout_OfLong = ClassDesc.of("java.lang.foreign.ValueLayout$OfLong");
    static final ClassDesc CD_ValueLayout_OfInt = ClassDesc.of("java.lang.foreign.ValueLayout$OfInt");
    static final ClassDesc CD_ValueLayout_OfShort = ClassDesc.of("java.lang.foreign.ValueLayout$OfShort");

    static final ClassDesc CD_UnsatisfiedLinkError = UnsatisfiedLinkError.class.describeConstable().orElseThrow();

    private static final MethodTypeDesc MTD_Arena = MethodTypeDesc.of(
        CD_Arena
    );
    private static final MethodTypeDesc MTD_link = MethodTypeDesc.of(
        ConstantDescs.CD_CallSite,
        ConstantDescs.CD_MethodHandles_Lookup,
        ConstantDescs.CD_String,
        ConstantDescs.CD_MethodType
    );
    static final MethodTypeDesc MTD_Linker = MethodTypeDesc.of(
        CD_Linker
    );
    static final MethodTypeDesc MTD_Linker_Option_int = MethodTypeDesc.of(
        CD_Linker_Option,
        ConstantDescs.CD_int
    );
    static final MethodTypeDesc MTD_Linker_Option_boolean = MethodTypeDesc.of(
        CD_Linker_Option,
        ConstantDescs.CD_boolean
    );
    static final MethodTypeDesc MTD_SymbolLookup = MethodTypeDesc.of(
        CD_SymbolLookup
    );
    static final MethodTypeDesc MTD_SymbolLookup_SymbolLookup = MethodTypeDesc.of(
        CD_SymbolLookup,
        CD_SymbolLookup
    );
    static final MethodTypeDesc MTD_Optional_String = MethodTypeDesc.of(
        CD_Optional,
        ConstantDescs.CD_String
    );
    static final MethodTypeDesc MTD_boolean = MethodTypeDesc.of(
        ConstantDescs.CD_boolean
    );
    static final MethodTypeDesc MTD_int = MethodTypeDesc.of(
        ConstantDescs.CD_int
    );
    static final MethodTypeDesc MTD_Object = MethodTypeDesc.of(
        ConstantDescs.CD_Object
    );
    static final MethodTypeDesc MTD_FunctionDescriptor_MemoryLayout_MemoryLayout_array = MethodTypeDesc.of(
        CD_FunctionDescriptor,
        CD_MemoryLayout,
        CD_MemoryLayout.arrayType()
    );
    static final MethodTypeDesc MTD_FunctionDescriptor_MemoryLayout_array = MethodTypeDesc.of(
        CD_FunctionDescriptor,
        CD_MemoryLayout.arrayType()
    );
    static final MethodTypeDesc MTD_Linker_Option_String_array = MethodTypeDesc.of(
        CD_Linker_Option,
        ConstantDescs.CD_String.arrayType()
    );
    static final MethodTypeDesc MTD_MethodHandle_MemorySegment_FunctionDescriptor_Linker_Option_array = MethodTypeDesc.of(
        ConstantDescs.CD_MethodHandle,
        CD_MemorySegment,
        CD_FunctionDescriptor,
        CD_Linker_Option_array
    );
    static final MethodTypeDesc MTD_void = MethodTypeDesc.of(
        ConstantDescs.CD_void
    );
    static final MethodTypeDesc MTD_void_MethodHandle = MethodTypeDesc.of(
        ConstantDescs.CD_void,
        ConstantDescs.CD_MethodHandle
    );
    static final MethodTypeDesc MTD_MethodHandle_MethodType = MethodTypeDesc.of(
        ConstantDescs.CD_MethodHandle,
        ConstantDescs.CD_MethodType
    );

    private static final Transformation c_long;
    private static final Transformation c_unsigned_long;
    private static final Transformation c_intptr_t;
    private static final Transformation c_uintptr_t;

    static {
        if (CPU.host().pointerSizeBits() == 32 || OS.current() == OS.WINDOWS) {
            c_long = Transformation.S32;
            c_unsigned_long = Transformation.U32;
        } else {
            c_long = Transformation.S64;
            c_unsigned_long = Transformation.S64;
        }
        if (CPU.host().pointerSizeBits() == 32) {
            c_intptr_t = Transformation.S32;
            c_uintptr_t = Transformation.U32;
        } else {
            c_intptr_t = Transformation.S64;
            c_uintptr_t = Transformation.U64;
        }
    }

}
