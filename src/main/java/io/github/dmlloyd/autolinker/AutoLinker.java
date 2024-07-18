package io.github.dmlloyd.autolinker;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.CallSite;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.dmlloyd.classfile.ClassBuilder;
import io.github.dmlloyd.classfile.ClassFile;
import io.github.dmlloyd.classfile.CodeBuilder;
import io.github.dmlloyd.classfile.TypeKind;
import io.github.dmlloyd.classfile.extras.reflect.AccessFlag;
import io.smallrye.common.constraint.Assert;
import io.smallrye.common.cpu.CPU;
import io.smallrye.common.os.OS;

/**
 * A native method auto-linker.
 */
public final class AutoLinker {

    /**
     * Construct a new instance.
     *
     * @param lookup the lookup to use for resolution (must not be {@code null})
     */
    public AutoLinker(final MethodHandles.Lookup lookup) {
        this.lookup = Assert.checkNotNullParam("lookup", lookup);
        this.classDesc = ClassDesc.of(lookup.lookupClass().getPackageName(), "$$AutoLinker");
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
     * Bootstrap method to dynamically link a native call site.
     *
     * @param lookup the lookup (must not be {@code null})
     * @param name the name of the native method (must not be {@code null})
     * @param type the downcall handle's method type (must not be {@code null})
     * @param getSymbolLookup the method handle to retrieve the {@link SymbolLookup} to use (must not be {@code null})
     * @param doLink the method handle for the target's {@code doLink} stub (must not be {@code null})
     * @param options the options list (must not be {@code null})
     * @return the linked call site (not {@code null})
     *
     * @throws IllegalArgumentException if an argument is of a disallowed type, value, or range
     * @throws UnsatisfiedLinkError if the native method is not found
     */
    public static CallSite link(final MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle getSymbolLookup, MethodHandle doLink, List<Linker.Option> options) {
        Linker linker = Linker.nativeLinker();
        SymbolLookup symLookup;
        try {
            symLookup = (SymbolLookup) getSymbolLookup.invokeExact();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UndeclaredThrowableException(e);
        }
        Optional<MemorySegment> optAddr = symLookup.or(linker.defaultLookup()).find(name);
        if (optAddr.isEmpty()) {
            throw new UnsatisfiedLinkError("No native symbol `" + name + "` was found to link against");
        }
        MemorySegment addr = optAddr.get();
        List<ValueLayout> argLayouts = type.parameterList().stream().map(AutoLinker::layoutForType).toList();

        MemoryLayout[] argArray = argLayouts.toArray(MemoryLayout[]::new);
        FunctionDescriptor funcDesc = type.returnType() == void.class ? FunctionDescriptor.ofVoid(argArray) : FunctionDescriptor.of(layoutForType(type.returnType()), argArray);
        //MethodHandle baseHandle = linker.downcallHandle(addr, funcDesc, options.toArray(Linker.Option[]::new));
        // let the generated class do the linkage so the correct module is access-checked
        MethodHandle downcallHandle;
        try {
            downcallHandle = (MethodHandle) doLink.invokeExact(linker, addr, funcDesc, (Linker.Option[]) options.toArray(Linker.Option[]::new));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UndeclaredThrowableException(e);
        }
        return new ConstantCallSite(downcallHandle);
    }

    private static ValueLayout layoutForType(Class<?> type) {
        return switch (TypeKind.from(type)) {
            case ByteType -> ValueLayout.JAVA_BYTE;
            case ShortType -> ValueLayout.JAVA_SHORT;
            case IntType -> ValueLayout.JAVA_INT;
            case FloatType -> ValueLayout.JAVA_FLOAT;
            case LongType -> ValueLayout.JAVA_LONG;
            case DoubleType -> ValueLayout.JAVA_DOUBLE;
            case ReferenceType -> ValueLayout.ADDRESS;
            case CharType -> ValueLayout.JAVA_CHAR;
            case BooleanType -> ValueLayout.JAVA_BOOLEAN;
            default -> throw new IllegalStateException();
        };
    }

    private final MethodHandles.Lookup lookup;
    private final ClassDesc classDesc;

    private final ClassValue<Object> linkables = new ClassValue<Object>() {
        protected Object computeValue(final Class<?> type) {
            if (! type.isInterface()) {
                throw new IllegalArgumentException(type + " is not an interface");
            }
            HashSet<Class<?>> visitedInterfaces = new HashSet<>();
            ArrayDeque<Class<?>> breadthQueue = new ArrayDeque<>();
            breadthQueue.add(type);
            populateQueue(type, breadthQueue, visitedInterfaces);
            ClassFile cf = ClassFile.of(ClassFile.StackMapsOption.GENERATE_STACK_MAPS);
            byte[] bytes = cf.build(classDesc, zb -> {
                zb.withFlags(AccessFlag.FINAL);
                zb.withVersion(ClassFile.JAVA_22_VERSION, 0);
                zb.withInterfaceSymbols(type.describeConstable().orElseThrow());
                // create trivial constructor
                zb.withMethod(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PRIVATE, mb -> {
                    mb.withCode(cb -> {
                        cb.aload(0);
                        cb.invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
                        cb.return_();
                    });
                });
                HashMap<String, HashSet<MethodType>> visitedMethods = new HashMap<>();
                for (Class<?> interface_ = breadthQueue.pollFirst(); interface_ != null; interface_ = breadthQueue.pollFirst()) {
                    processMethods(zb, type, visitedMethods);
                }
            });
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

        private void populateQueue(final Class<?> type, final ArrayDeque<Class<?>> breadthQueue, final HashSet<Class<?>> visitedInterfaces) {
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

        private void processMethods(final ClassBuilder zb, final Class<?> interface_, final HashMap<String, HashSet<MethodType>> visitedMethods) {
            for (Method method : interface_.getDeclaredMethods()) {
                int mods = method.getModifiers();
                if (Modifier.isStatic(mods) || ! Modifier.isAbstract(mods)) {
                    continue;
                }
                Link link = method.getAnnotation(Link.class);
                if (link == null) {
                    // just don't implement it
                    continue;
                }
                List<Transformation> transformations = new ArrayList<>(method.getParameterCount() + 4);
                Parameter[] parameters = method.getParameters();
                for (final Parameter parameter : parameters) {
                    if (parameter.getAnnotation(Link.va_start.class) != null) {
                        transformations.add(Transformation.START_VA);
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
                Link.capture[] captureAnnotations = method.getAnnotationsByType(Link.capture.class);
                Set<String> capture = captureAnnotations == null ? Set.of() : Stream.of(captureAnnotations).map(Link.capture::value).collect(Collectors.toUnmodifiableSet());
                Link.critical critical = method.getAnnotation(Link.critical.class);

                MethodType type = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
                if (visitedMethods.computeIfAbsent(method.getName(), AutoLinker::newHashSet).add(type)) {
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
                                int argCnt = (int) transformations.stream().map(Transformation::layout).filter(Objects::nonNull).count();
                                pushInt(tb, argCnt);
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
                                int optCnt = (capture.isEmpty() ? 0 : 1) + (critical != null ? 1 : 0) + (int) transformations.stream().filter(Transformation::hasOption).count();
                                pushInt(tb, optCnt);
                                tb.anewarray(CD_Linker_Option);
                                idx = 0;
                                int argIdx = 0;
                                for (Transformation transformation : transformations) {
                                    if (transformation.hasOption()) {
                                        tb.dup();
                                        pushInt(tb, idx ++);
                                        transformation.applyOption(tb, argIdx);
                                        tb.aastore();
                                    }
                                    if (transformation.consumeArgument()) {
                                        argIdx++;
                                    }
                                }
                                if (! capture.isEmpty()) {
                                    tb.dup();
                                    pushInt(tb, idx ++);
                                    pushInt(tb, capture.size());
                                    tb.anewarray(ConstantDescs.CD_String);
                                    int capIdx = 0;
                                    for (String cap : capture) {
                                        tb.dup();
                                        pushInt(tb, capIdx++);
                                        tb.ldc(cap);
                                        tb.aastore();
                                    }
                                    tb.invokestatic(CD_Linker_Option, "captureCallState", MTD_Linker_Option_String_array, true);
                                    tb.aastore();
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
                                // now make a constant call site for it
                                tb.new_(CD_ConstantCallSite);
                                // stack: handle ccs
                                tb.dup_x1();
                                // stack: ccs handle ccs
                                tb.swap();
                                // stack: ccs ccs handle
                                tb.invokespecial(CD_ConstantCallSite, ConstantDescs.INIT_NAME, MTD_void_MethodHandle);
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
                            cb.invokespecial(CD_UnsatisfiedLinkError, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
                            // stack: ule
                            cb.athrow();
                            // stack: -- (done)
                        });
                    });
                    // add the method
                    zb.withMethod(method.getName(), type.describeConstable().orElseThrow(), ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SYNTHETIC, mb -> {
                        mb.withCode(cb -> {
                            int s = 1;
                            final Iterator<Transformation> iterator = transformations.iterator();
                            for (Class<?> argType : method.getParameterTypes()) {
                                while (iterator.hasNext()) {
                                    final Transformation transformation = iterator.next();
                                    if (NativeEnum.class.isAssignableFrom(argType)) {
                                        cb.invokevirtual(argType.describeConstable().orElseThrow(), "nativeCode", MTD_int);
                                        transformation.applyArgument(cb, s, int.class);
                                    } else {
                                        transformation.applyArgument(cb, s, argType);
                                    }
                                    if (transformation.consumeArgument()) {
                                        s += TypeKind.from(argType).slotSize();
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
                                MethodTypeDesc.of(
                                    returnTransformation == Transformation.VOID ? ConstantDescs.CD_void : returnTransformation.layout().carrier().describeConstable().orElseThrow(),
                                    transformations.stream().map(Transformation::layout).filter(Objects::nonNull).map(ValueLayout::carrier).map(Class::describeConstable).map(Optional::orElseThrow).toArray(ClassDesc[]::new)
                                )
                            ));
                            Class<?> returnType = method.getReturnType();
                            if (NativeEnum.class.isAssignableFrom(returnType)) {
                                returnTransformation.emitReturn(cb, int.class);
                                cb.invokestatic(returnType.describeConstable().orElseThrow(), "fromNativeCode", MethodTypeDesc.of(returnType.describeConstable().orElseThrow(), ConstantDescs.CD_int));
                                cb.areturn();
                            } else {
                                returnTransformation.emitReturn(cb, returnType);
                                cb.return_(TypeKind.from(returnType));
                            }
                        });
                    });
                }
            }
        }
    };

    private static void pushInt(CodeBuilder cb, int val) {
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

    private Transformation transformationFor(final AsType asType) {
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

    static final ClassDesc CD_AddressLayout = AddressLayout.class.describeConstable().orElseThrow();
    static final ClassDesc CD_Buffer = Buffer.class.describeConstable().orElseThrow();
    static final ClassDesc CD_ByteBuffer = ByteBuffer.class.describeConstable().orElseThrow();
    static final ClassDesc CD_ConstantCallSite = ConstantCallSite.class.describeConstable().orElseThrow();
    static final ClassDesc CD_FunctionDescriptor = FunctionDescriptor.class.describeConstable().orElseThrow();
    static final ClassDesc CD_Linker = Linker.class.describeConstable().orElseThrow();
    static final ClassDesc CD_Linker_Option = Linker.Option.class.describeConstable().orElseThrow();
    static final ClassDesc CD_Linker_Option_array = Linker.Option[].class.describeConstable().orElseThrow();
    static final ClassDesc CD_MemoryLayout = MemoryLayout.class.describeConstable().orElseThrow();
    static final ClassDesc CD_MemorySegment = MemorySegment.class.describeConstable().orElseThrow();
    static final ClassDesc CD_Optional = Optional.class.describeConstable().orElseThrow();
    static final ClassDesc CD_SymbolLookup = SymbolLookup.class.describeConstable().orElseThrow();
    static final ClassDesc CD_ValueLayout = ValueLayout.class.describeConstable().orElseThrow();
    static final ClassDesc CD_ValueLayout_OfBoolean = ValueLayout.OfBoolean.class.describeConstable().orElseThrow();
    static final ClassDesc CD_ValueLayout_OfDouble = ValueLayout.OfDouble.class.describeConstable().orElseThrow();
    static final ClassDesc CD_ValueLayout_OfFloat = ValueLayout.OfFloat.class.describeConstable().orElseThrow();
    static final ClassDesc CD_ValueLayout_OfLong = ValueLayout.OfLong.class.describeConstable().orElseThrow();
    static final ClassDesc CD_ValueLayout_OfInt = ValueLayout.OfInt.class.describeConstable().orElseThrow();
    static final ClassDesc CD_ValueLayout_OfShort = ValueLayout.OfShort.class.describeConstable().orElseThrow();

    static final ClassDesc CD_UnsatisfiedLinkError = UnsatisfiedLinkError.class.describeConstable().orElseThrow();

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
    static final MethodTypeDesc MTD_void_MethodHandle = MethodTypeDesc.of(
        ConstantDescs.CD_void,
        ConstantDescs.CD_MethodHandle
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
