package io.github.dmlloyd.autolinker;

import static io.github.dmlloyd.autolinker.AutoLinker.CD_AddressLayout;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_Linker_Option;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout_OfBoolean;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout_OfDouble;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout_OfFloat;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout_OfInt;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout_OfLong;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout_OfShort;
import static io.github.dmlloyd.autolinker.AutoLinker.MTD_Linker_Option_int;

import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import io.github.dmlloyd.classfile.CodeBuilder;
import io.github.dmlloyd.classfile.TypeKind;

/**
 * The transformations which apply between a value layout and a method argument or return value.
 */
enum Transformation {
    /**
     * An unsigned 7-bit integer (i.e. an ASCII character).
     */
    U7(ValueLayout.JAVA_INT) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            switch (TypeKind.from(argType)) {
                case ByteType, ShortType, CharType, IntType -> {
                    cb.iload(varIdx);
                    cb.bipush(0x7f);
                    cb.iand();
                }
                case LongType -> {
                    cb.lload(varIdx);
                    cb.l2i();
                    cb.bipush(0x7f);
                    cb.iand();
                }
                case BooleanType -> cb.iload(varIdx);
                default -> throw invalidArgType(this, argType);
            }
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case ByteType, ShortType, CharType, IntType -> {
                    cb.bipush(0x7f);
                    cb.iand();
                }
                case LongType -> {
                    cb.bipush(0x7f);
                    cb.iand();
                    cb.i2l();
                }
                case BooleanType -> {
                    cb.bipush(0x7f);
                    cb.iand();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VoidType -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }
    },
    /**
     * A signed 8-bit integer.
     */
    S8(ValueLayout.JAVA_INT) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            switch (TypeKind.from(argType)) {
                case ByteType, BooleanType -> cb.iload(varIdx);
                case ShortType, IntType -> {
                    cb.iload(varIdx);
                    cb.i2b();
                }
                case LongType -> {
                    cb.lload(varIdx);
                    cb.l2i();
                    cb.i2b();
                }
                default -> throw invalidArgType(this, argType);
            }
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case ByteType, ShortType, IntType -> cb.i2b();
                case LongType -> {
                    cb.i2b();
                    cb.i2l();
                }
                case BooleanType -> {
                    cb.i2b();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VoidType -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }
    },
    /**
     * An unsigned 8-bit integer.
     */
    U8(ValueLayout.JAVA_INT) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            switch (TypeKind.from(argType)) {
                case ByteType -> {
                    cb.iload(varIdx);
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                }
                case ShortType, CharType, IntType -> {
                    cb.iload(varIdx);
                    cb.i2b();
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                }
                case LongType -> {
                    cb.lload(varIdx);
                    cb.l2i();
                    cb.i2b();
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                }
                case BooleanType -> cb.iload(varIdx);
                default -> throw invalidArgType(this, argType);
            }
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case ByteType -> cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                case ShortType, CharType, IntType -> {
                    cb.i2b();
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                }
                case LongType -> {
                    cb.i2b();
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                    // zero-extends
                    cb.i2l();
                }
                case BooleanType -> {
                    cb.i2b();
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VoidType -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }
    },
    /**
     * A signed 16-bit integer.
     */
    S16(ValueLayout.JAVA_SHORT) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            switch (TypeKind.from(argType)) {
                case ByteType, ShortType, BooleanType -> cb.iload(varIdx);
                case IntType -> {
                    cb.iload(varIdx);
                    cb.i2s();
                }
                case LongType -> {
                    cb.lload(varIdx);
                    cb.l2i();
                    cb.i2s();
                }
                default -> throw invalidArgType(this, argType);
            }
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case ByteType, ShortType -> {}
                case IntType -> cb.i2s();
                case LongType -> {
                    cb.i2s();
                    cb.i2l();
                }
                case BooleanType -> {
                    cb.i2s();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VoidType -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }

        public void emitLayout(final CodeBuilder cb) {
            cb.getstatic(CD_ValueLayout, "JAVA_SHORT", CD_ValueLayout_OfShort);
        }
    },
    /**
     * An unsigned 16-bit integer.
     */
    U16(ValueLayout.JAVA_INT) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            switch (TypeKind.from(argType)) {
                case CharType, BooleanType -> cb.iload(varIdx);
                case ShortType, ByteType, IntType -> {
                    cb.iload(varIdx);
                    cb.i2c();
                }
                case LongType -> {
                    cb.lload(varIdx);
                    cb.l2i();
                    cb.i2c();
                }
                default -> throw invalidArgType(this, argType);
            }
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case ByteType, ShortType -> {}
                case IntType -> cb.i2c();
                case LongType -> {
                    cb.i2c();
                    cb.i2l();
                }
                case BooleanType -> {
                    cb.i2c();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VoidType -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }
    },
    /**
     * A signed 32-bit integer.
     */
    S32(ValueLayout.JAVA_INT) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            switch (TypeKind.from(argType)) {
                case ByteType, ShortType, CharType, IntType, BooleanType -> cb.iload(varIdx);
                case LongType -> {
                    cb.lload(varIdx);
                    cb.l2i();
                }
                default -> throw invalidArgType(this, argType);
            }
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case ByteType, CharType, ShortType, IntType -> {}
                case LongType -> cb.i2l();
                case BooleanType -> cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                case VoidType -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }
    },
    /**
     * An unsigned 32-bit integer.
     */
    U32(ValueLayout.JAVA_INT) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            switch (TypeKind.from(argType)) {
                case ByteType -> {
                    cb.iload(varIdx);
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                }
                case ShortType -> {
                    cb.iload(varIdx);
                    cb.invokestatic(ConstantDescs.CD_Short, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_short));
                }
                case CharType, IntType, BooleanType -> cb.iload(varIdx);
                case LongType -> {
                    cb.lload(varIdx);
                    cb.l2i();
                }
                default -> throw invalidArgType(this, argType);
            }
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case ByteType -> cb.i2b();
                case IntType -> {}
                case ShortType, CharType -> cb.i2c();
                case LongType -> cb.invokestatic(ConstantDescs.CD_Integer, "toUnsignedLong", MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_int));
                case BooleanType -> cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                case VoidType -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }
    },
    /**
     * A 64-bit signed integer.
     */
    S64(ValueLayout.JAVA_LONG) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            switch (TypeKind.from(argType)) {
                case ByteType, ShortType, IntType, CharType, BooleanType -> {
                    cb.iload(varIdx);
                    cb.i2l();
                }
                case LongType -> cb.lload(varIdx);
                default -> throw invalidArgType(this, argType);
            }
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case ByteType -> {
                    cb.l2i();
                    cb.i2b();
                }
                case CharType -> {
                    cb.l2i();
                    cb.i2c();
                }
                case ShortType -> {
                    cb.l2i();
                    cb.i2s();
                }
                case IntType -> cb.l2i();
                case LongType -> {}
                case BooleanType -> {
                    cb.lconst_0();
                    cb.lcmp();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VoidType -> cb.pop2();
                default -> throw invalidReturnType(this, returnType);
            }
        }

        public void emitLayout(final CodeBuilder cb) {
            cb.getstatic(CD_ValueLayout, "JAVA_LONG", CD_ValueLayout_OfLong);
        }
    },
    /**
     * A 64-bit unsigned integer.
     */
    U64(ValueLayout.JAVA_LONG) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            switch (TypeKind.from(argType)) {
                case ByteType -> {
                    cb.iload(varIdx);
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                    cb.i2l();
                }
                case ShortType -> {
                    cb.iload(varIdx);
                    cb.invokestatic(ConstantDescs.CD_Short, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_short));
                    cb.i2l();
                }
                case IntType -> {
                    cb.iload(varIdx);
                    cb.invokestatic(ConstantDescs.CD_Integer, "toUnsignedLong", MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_int));
                }
                case CharType, BooleanType -> {
                    cb.iload(varIdx);
                    cb.i2l();
                }
                case LongType -> cb.lload(varIdx);
                default -> throw invalidArgType(this, argType);
            }
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case ByteType -> {
                    cb.l2i();
                    cb.i2b();
                }
                case CharType -> {
                    cb.l2i();
                    cb.i2c();
                }
                case ShortType -> {
                    cb.l2i();
                    cb.i2s();
                }
                case IntType -> cb.l2i();
                case LongType -> {}
                case BooleanType -> {
                    cb.lconst_0();
                    cb.lcmp();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VoidType -> cb.pop2();
                default -> throw invalidReturnType(this, returnType);
            }
        }

        public void emitLayout(final CodeBuilder cb) {
            cb.getstatic(CD_ValueLayout, "JAVA_LONG", CD_ValueLayout_OfLong);
        }
    },
    /**
     * A 32-bit floating point value.
     */
    F32(ValueLayout.JAVA_FLOAT) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            switch (TypeKind.from(argType)) {
                case ByteType, ShortType, CharType, IntType, BooleanType -> {
                    cb.iload(varIdx);
                    cb.i2f();
                }
                case FloatType -> cb.fload(varIdx);
                case DoubleType -> {
                    cb.dload(varIdx);
                    cb.d2f();
                }
                case LongType -> {
                    cb.lload(varIdx);
                    cb.l2f();
                }
                default -> throw invalidArgType(this, argType);
            }
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case IntType -> cb.f2i();
                case LongType -> cb.f2l();
                case FloatType -> {}
                case DoubleType -> cb.f2d();
                case BooleanType -> {
                    cb.fconst_0();
                    cb.fcmpg();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VoidType -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }

        public void emitLayout(final CodeBuilder cb) {
            cb.getstatic(CD_ValueLayout, "JAVA_FLOAT", CD_ValueLayout_OfFloat);
        }
    },
    /**
     * A 64-bit floating point value.
     */
    F64(ValueLayout.JAVA_DOUBLE) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            switch (TypeKind.from(argType)) {
                case ByteType, ShortType, CharType, IntType, BooleanType -> {
                    cb.iload(varIdx);
                    cb.i2d();
                }
                case FloatType -> {
                    cb.fload(varIdx);
                    cb.f2d();
                }
                case DoubleType -> cb.dload(varIdx);
                case LongType -> {
                    cb.lload(varIdx);
                    cb.l2d();
                }
                default -> throw invalidArgType(this, argType);
            }
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case IntType -> cb.d2i();
                case LongType -> cb.d2l();
                case FloatType -> cb.d2f();
                case DoubleType -> {}
                case BooleanType -> {
                    cb.dconst_0();
                    cb.dcmpg();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VoidType -> cb.pop2();
                default -> throw invalidReturnType(this, returnType);
            }
        }

        public void emitLayout(final CodeBuilder cb) {
            cb.getstatic(CD_ValueLayout, "JAVA_DOUBLE", CD_ValueLayout_OfDouble);
        }
    },
    /**
     * A pointer (native sized).
     */
    PTR(ValueLayout.ADDRESS) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            switch (TypeKind.from(argType)) {
                case ReferenceType -> {
                    if (argType.isArray()) {
                        switch (TypeKind.from(argType.componentType())) {
                            case ByteType -> {
                                cb.aload(varIdx);
                                cb.invokestatic(AutoLinker.CD_MemorySegment, "ofArray", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, ConstantDescs.CD_byte.arrayType()), true);
                            }
                            case CharType -> {
                                cb.aload(varIdx);
                                cb.invokestatic(AutoLinker.CD_MemorySegment, "ofArray", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, ConstantDescs.CD_char.arrayType()), true);
                            }
                            case ShortType -> {
                                cb.aload(varIdx);
                                cb.invokestatic(AutoLinker.CD_MemorySegment, "ofArray", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, ConstantDescs.CD_short.arrayType()), true);
                            }
                            case IntType -> {
                                cb.aload(varIdx);
                                cb.invokestatic(AutoLinker.CD_MemorySegment, "ofArray", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, ConstantDescs.CD_int.arrayType()), true);
                            }
                            case LongType -> {
                                cb.aload(varIdx);
                                cb.invokestatic(AutoLinker.CD_MemorySegment, "ofArray", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, ConstantDescs.CD_long.arrayType()), true);
                            }
                            case FloatType -> {
                                cb.aload(varIdx);
                                cb.invokestatic(AutoLinker.CD_MemorySegment, "ofArray", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, ConstantDescs.CD_float.arrayType()), true);
                            }
                            case DoubleType -> {
                                cb.aload(varIdx);
                                cb.invokestatic(AutoLinker.CD_MemorySegment, "ofArray", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, ConstantDescs.CD_double.arrayType()), true);
                            }
                            default -> throw invalidArgType(this, argType);
                        }
                    } else {
                        switch (argType.getName()) {
                            case "java.lang.foreign.MemorySegment" -> cb.aload(varIdx);
                            case "java.nio.ByteBuffer" -> {
                                cb.aload(varIdx);
                                cb.invokestatic(AutoLinker.CD_MemorySegment, "ofBuffer", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, AutoLinker.CD_Buffer), true);
                            }
                            default -> throw invalidArgType(this, argType);
                        }
                    }
                }
                default -> throw invalidArgType(this, argType);
            }
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case ReferenceType -> {
                    switch (returnType.getName()) {
                        case "java.lang.foreign.MemorySegment" -> {}
                        case "java.nio.ByteBuffer" -> cb.invokestatic(AutoLinker.CD_MemorySegment, "asByteBuffer", MethodTypeDesc.of(AutoLinker.CD_ByteBuffer), true);
                        default -> throw invalidReturnType(this, returnType);
                    }
                }
                case VoidType -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }

        public void emitLayout(final CodeBuilder cb) {
            cb.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
        }
    },
    /**
     * A boolean value (i.e. C's {@code _Bool} type).
     */
    BOOL(ValueLayout.JAVA_BOOLEAN) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            switch (TypeKind.from(argType)) {
                case BooleanType -> cb.iload(varIdx);
                default -> throw invalidArgType(this, argType);
            }
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case BooleanType -> cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                case VoidType -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }

        public void emitLayout(final CodeBuilder cb) {
            cb.getstatic(CD_ValueLayout, "JAVA_BOOLEAN", CD_ValueLayout_OfBoolean);
        }
    },
    /**
     * A {@code void} return type or an argument to drop.
     */
    VOID(null) {
        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            // drop this argument
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            TypeKind tk = TypeKind.from(returnType);
            switch (tk) {
                case ByteType, ShortType, IntType, CharType, BooleanType -> cb.iconst_0();
                case FloatType -> cb.fconst_0();
                case DoubleType -> cb.dconst_0();
                case LongType -> cb.lconst_0();
                case ReferenceType -> cb.aconst_null();
                case VoidType -> {}
            }
        }

        public boolean hasLayout() {
            return false;
        }
    },
    /**
     * A marker indicating the start of variable arguments.
     */
    START_VA(null) {
        public void applyOption(final CodeBuilder cb, final int argIdx) {
            switch (argIdx) {
                case 0 -> cb.iconst_0();
                case 1 -> cb.iconst_1();
                case 2 -> cb.iconst_2();
                case 3 -> cb.iconst_3();
                case 4 -> cb.iconst_4();
                case 5 -> cb.iconst_5();
                default -> {
                    if (argIdx < 128) {
                        cb.bipush(argIdx);
                    } else {
                        cb.sipush(argIdx);
                    }
                }
            }
            cb.invokestatic(CD_Linker_Option, "firstVariadicArg", MTD_Linker_Option_int, true);
        }

        public boolean hasOption() {
            return true;
        }

        public void applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType) {
            // no arguments consumed
        }

        public boolean hasLayout() {
            return false;
        }

        public boolean consumeArgument() {
            return false;
        }
    },
    ;

    private static IllegalArgumentException invalidArgType(final Transformation xform, final Class<?> argType) {
        return new IllegalArgumentException("Cannot use transformation " + xform + " with argument type " + argType);
    }

    private static IllegalArgumentException invalidReturnType(final Transformation xform, final Class<?> argType) {
        return new IllegalArgumentException("Cannot use transformation " + xform + " with return type " + argType);
    }

    private final ValueLayout layout;

    Transformation(final ValueLayout layout) {
        this.layout = layout;
    }

    static Transformation forJavaType(final Class<?> type) {
        return switch (TypeKind.from(type)) {
            case ByteType -> S8;
            case ShortType -> S16;
            case IntType -> S32;
            case FloatType -> F32;
            case LongType -> S64;
            case DoubleType -> F64;
            case ReferenceType -> {
                if (type.isArray() || type == MemorySegment.class) {
                    yield PTR;
                } else if (NativeEnum.class.isAssignableFrom(type)) {
                    yield S32;
                } else {
                    throw new IllegalArgumentException("No conversion for Java type " + type);
                }
            }
            case CharType -> U16;
            case BooleanType -> BOOL;
            case VoidType -> VOID;
        };
    }

    /**
     * {@return the native-side value layout for this transformation}
     */
    public ValueLayout layout() {
        return layout;
    }

    /**
     * Apply the argument for this transformation into a call which is being built.
     *
     * @param cb      the code builder (not {@code null})
     * @param varIdx  the argument's variable slot index
     * @param argType the type of the argument (not {@code null})
     */
    public void applyArgument(CodeBuilder cb, final int varIdx, final Class<?> argType) {
        throw new IllegalArgumentException("This type cannot be used as an argument type");
    }

    /**
     * Emit the return instruction for this transformation.
     *
     * @param cb         the code builder (not {@code null})
     * @param returnType the return type (not {@code null})
     */
    public void emitReturn(CodeBuilder cb, final Class<?> returnType) {
        throw new IllegalArgumentException("This type cannot be used as a return type");
    }

    /**
     * Emit code to optionally push an option on to the stack.
     *
     * @param cb     the code builder (not {@code null})
     * @param argIdx the argument index
     */
    public void applyOption(CodeBuilder cb, final int argIdx) {
    }

    /**
     * {@return <code>true</code> when this transformation consumes an argument or <code>false</code> when it does not}
     */
    public boolean consumeArgument() {
        return true;
    }

    /**
     * Emit code to produce the layout for this value, if there is one.
     *
     * @param cb the code builder (not {@code null})
     */
    public void emitLayout(final CodeBuilder cb) {
        cb.getstatic(CD_ValueLayout, "JAVA_INT", CD_ValueLayout_OfInt);
    }

    /**
     * {@return true if there is a layout for this transformation, or false if there is not}
     */
    public boolean hasLayout() {
        return true;
    }

    /**
     * {@return <code>true</code> if this transformation emits an option}
     */
    public boolean hasOption() {
        return false;
    }
}
