package io.github.dmlloyd.autolinker;

import static io.github.dmlloyd.autolinker.AutoLinker.CD_AddressLayout;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_Linker_Option;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_MemorySegment;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout_OfBoolean;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout_OfDouble;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout_OfFloat;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout_OfInt;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout_OfLong;
import static io.github.dmlloyd.autolinker.AutoLinker.CD_ValueLayout_OfShort;
import static io.github.dmlloyd.autolinker.AutoLinker.MTD_Linker_Option_String_array;
import static io.github.dmlloyd.autolinker.AutoLinker.MTD_Linker_Option_int;
import static io.github.dmlloyd.autolinker.AutoLinker.pushInt;
import static io.github.dmlloyd.autolinker.Direction.in_out;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Parameter;
import java.util.function.Consumer;

import io.github.dmlloyd.classfile.CodeBuilder;
import io.github.dmlloyd.classfile.TypeKind;

/**
 * The transformations which apply between a value layout and a method argument or return value.
 */
enum Transformation {
    /**
     * An unsigned 7-bit integer (i.e. an ASCII character).
     */
    U7 {
        public Class<?> carrier() {
            return int.class;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            switch (TypeKind.from(argType)) {
                case BYTE, SHORT, CHAR, INT -> {
                    cb.iload(varIdx);
                    cb.bipush(0x7f);
                    cb.iand();
                }
                case LONG -> {
                    cb.lload(varIdx);
                    cb.l2i();
                    cb.bipush(0x7f);
                    cb.iand();
                }
                case BOOLEAN -> cb.iload(varIdx);
                default -> throw invalidArgType(this, argType);
            }
            return null;
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case BYTE, SHORT, CHAR, INT -> {
                    cb.bipush(0x7f);
                    cb.iand();
                }
                case LONG -> {
                    cb.bipush(0x7f);
                    cb.iand();
                    cb.i2l();
                }
                case BOOLEAN -> {
                    cb.bipush(0x7f);
                    cb.iand();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VOID -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }
    },
    /**
     * A signed 8-bit integer.
     */
    S8 {
        public Class<?> carrier() {
            return int.class;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            switch (TypeKind.from(argType)) {
                case BYTE, BOOLEAN -> cb.iload(varIdx);
                case SHORT, INT -> {
                    cb.iload(varIdx);
                    cb.i2b();
                }
                case LONG -> {
                    cb.lload(varIdx);
                    cb.l2i();
                    cb.i2b();
                }
                default -> throw invalidArgType(this, argType);
            }
            return null;
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case BYTE, SHORT, INT -> cb.i2b();
                case LONG -> {
                    cb.i2b();
                    cb.i2l();
                }
                case BOOLEAN -> {
                    cb.i2b();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VOID -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }
    },
    /**
     * An unsigned 8-bit integer.
     */
    U8 {
        public Class<?> carrier() {
            return int.class;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            switch (TypeKind.from(argType)) {
                case BYTE -> {
                    cb.iload(varIdx);
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                }
                case SHORT, CHAR, INT -> {
                    cb.iload(varIdx);
                    cb.i2b();
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                }
                case LONG -> {
                    cb.lload(varIdx);
                    cb.l2i();
                    cb.i2b();
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                }
                case BOOLEAN -> cb.iload(varIdx);
                default -> throw invalidArgType(this, argType);
            }
            return null;
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case BYTE -> cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                case SHORT, CHAR, INT -> {
                    cb.i2b();
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                }
                case LONG -> {
                    cb.i2b();
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                    // zero-extends
                    cb.i2l();
                }
                case BOOLEAN -> {
                    cb.i2b();
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VOID -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }
    },
    /**
     * A signed 16-bit integer.
     */
    S16 {
        public Class<?> carrier() {
            return short.class;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            switch (TypeKind.from(argType)) {
                case BYTE, SHORT, BOOLEAN -> cb.iload(varIdx);
                case INT -> {
                    cb.iload(varIdx);
                    cb.i2s();
                }
                case LONG -> {
                    cb.lload(varIdx);
                    cb.l2i();
                    cb.i2s();
                }
                default -> throw invalidArgType(this, argType);
            }
            return null;
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case BYTE, SHORT -> {}
                case INT -> cb.i2s();
                case LONG -> {
                    cb.i2s();
                    cb.i2l();
                }
                case BOOLEAN -> {
                    cb.i2s();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VOID -> cb.pop();
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
    U16 {
        public Class<?> carrier() {
            return int.class;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            switch (TypeKind.from(argType)) {
                case CHAR, BOOLEAN -> cb.iload(varIdx);
                case SHORT, BYTE, INT -> {
                    cb.iload(varIdx);
                    cb.i2c();
                }
                case LONG -> {
                    cb.lload(varIdx);
                    cb.l2i();
                    cb.i2c();
                }
                default -> throw invalidArgType(this, argType);
            }
            return null;
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case BYTE, SHORT -> {}
                case INT -> cb.i2c();
                case LONG -> {
                    cb.i2c();
                    cb.i2l();
                }
                case BOOLEAN -> {
                    cb.i2c();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VOID -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }
    },
    /**
     * A signed 32-bit integer.
     */
    S32 {
        public Class<?> carrier() {
            return int.class;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            switch (TypeKind.from(argType)) {
                case BYTE, SHORT, CHAR, INT, BOOLEAN -> cb.iload(varIdx);
                case LONG -> {
                    cb.lload(varIdx);
                    cb.l2i();
                }
                default -> throw invalidArgType(this, argType);
            }
            return null;
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case BYTE, CHAR, SHORT, INT -> {}
                case LONG -> cb.i2l();
                case BOOLEAN -> cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                case VOID -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }
    },
    /**
     * An unsigned 32-bit integer.
     */
    U32 {
        public Class<?> carrier() {
            return int.class;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            switch (TypeKind.from(argType)) {
                case BYTE -> {
                    cb.iload(varIdx);
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                }
                case SHORT -> {
                    cb.iload(varIdx);
                    cb.invokestatic(ConstantDescs.CD_Short, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_short));
                }
                case CHAR, INT, BOOLEAN -> cb.iload(varIdx);
                case LONG -> {
                    cb.lload(varIdx);
                    cb.l2i();
                }
                default -> throw invalidArgType(this, argType);
            }
            return null;
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case BYTE -> cb.i2b();
                case INT -> {}
                case SHORT, CHAR -> cb.i2c();
                case LONG -> cb.invokestatic(ConstantDescs.CD_Integer, "toUnsignedLong", MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_int));
                case BOOLEAN -> cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                case VOID -> cb.pop();
                default -> throw invalidReturnType(this, returnType);
            }
        }
    },
    /**
     * A 64-bit signed integer.
     */
    S64 {
        public Class<?> carrier() {
            return long.class;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            switch (TypeKind.from(argType)) {
                case BYTE, SHORT, INT, CHAR, BOOLEAN -> {
                    cb.iload(varIdx);
                    cb.i2l();
                }
                case LONG -> cb.lload(varIdx);
                default -> throw invalidArgType(this, argType);
            }
            return null;
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case BYTE -> {
                    cb.l2i();
                    cb.i2b();
                }
                case CHAR -> {
                    cb.l2i();
                    cb.i2c();
                }
                case SHORT -> {
                    cb.l2i();
                    cb.i2s();
                }
                case INT -> cb.l2i();
                case LONG -> {}
                case BOOLEAN -> {
                    cb.lconst_0();
                    cb.lcmp();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VOID -> cb.pop2();
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
    U64 {
        public Class<?> carrier() {
            return long.class;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            switch (TypeKind.from(argType)) {
                case BYTE -> {
                    cb.iload(varIdx);
                    cb.invokestatic(ConstantDescs.CD_Byte, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_byte));
                    cb.i2l();
                }
                case SHORT -> {
                    cb.iload(varIdx);
                    cb.invokestatic(ConstantDescs.CD_Short, "toUnsignedInt", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_short));
                    cb.i2l();
                }
                case INT -> {
                    cb.iload(varIdx);
                    cb.invokestatic(ConstantDescs.CD_Integer, "toUnsignedLong", MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_int));
                }
                case CHAR, BOOLEAN -> {
                    cb.iload(varIdx);
                    cb.i2l();
                }
                case LONG -> cb.lload(varIdx);
                default -> throw invalidArgType(this, argType);
            }
            return null;
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case BYTE -> {
                    cb.l2i();
                    cb.i2b();
                }
                case CHAR -> {
                    cb.l2i();
                    cb.i2c();
                }
                case SHORT -> {
                    cb.l2i();
                    cb.i2s();
                }
                case INT -> cb.l2i();
                case LONG -> {}
                case BOOLEAN -> {
                    cb.lconst_0();
                    cb.lcmp();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VOID -> cb.pop2();
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
    F32 {
        public Class<?> carrier() {
            return float.class;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            switch (TypeKind.from(argType)) {
                case BYTE, SHORT, CHAR, INT, BOOLEAN -> {
                    cb.iload(varIdx);
                    cb.i2f();
                }
                case FLOAT -> cb.fload(varIdx);
                case DOUBLE -> {
                    cb.dload(varIdx);
                    cb.d2f();
                }
                case LONG -> {
                    cb.lload(varIdx);
                    cb.l2f();
                }
                default -> throw invalidArgType(this, argType);
            }
            return null;
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case INT -> cb.f2i();
                case LONG -> cb.f2l();
                case FLOAT -> {}
                case DOUBLE -> cb.f2d();
                case BOOLEAN -> {
                    cb.fconst_0();
                    cb.fcmpg();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VOID -> cb.pop();
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
    F64 {
        public Class<?> carrier() {
            return double.class;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            switch (TypeKind.from(argType)) {
                case BYTE, SHORT, CHAR, INT, BOOLEAN -> {
                    cb.iload(varIdx);
                    cb.i2d();
                }
                case FLOAT -> {
                    cb.fload(varIdx);
                    cb.f2d();
                }
                case DOUBLE -> cb.dload(varIdx);
                case LONG -> {
                    cb.lload(varIdx);
                    cb.l2d();
                }
                default -> throw invalidArgType(this, argType);
            }
            return null;
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case INT -> cb.d2i();
                case LONG -> cb.d2l();
                case FLOAT -> cb.d2f();
                case DOUBLE -> {}
                case BOOLEAN -> {
                    cb.dconst_0();
                    cb.dcmpg();
                    cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case VOID -> cb.pop2();
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
    PTR {
        public Class<?> carrier() {
            return LazyLink.MEMORY_SEGMENT;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, Direction dir) {
            switch (TypeKind.from(argType)) {
                case REFERENCE -> {
                    if (argType.isArray()) {
                        Class<?> componentType = argType.componentType();
                        if (componentType.isPrimitive()) {
                            ClassDesc arrayType = componentType.describeConstable().orElseThrow().arrayType();
                            if (heap) {
                                cb.aload(varIdx);
                                cb.invokestatic(AutoLinker.CD_MemorySegment, "ofArray", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, arrayType), true);
                            } else {
                                cb.aload(arenaVar);
                                if (dir == null) {
                                    dir = in_out;
                                }
                                int copySlot;
                                if (dir.in()) {
                                    switch (TypeKind.from(componentType)) {
                                        case BYTE -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_BYTE", AutoLinker.CD_ValueLayout_OfByte);
                                        case CHAR -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_CHAR", AutoLinker.CD_ValueLayout_OfChar);
                                        case SHORT -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_SHORT", AutoLinker.CD_ValueLayout_OfShort);
                                        case INT -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_INT", AutoLinker.CD_ValueLayout_OfInt);
                                        case LONG -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_LONG", AutoLinker.CD_ValueLayout_OfLong);
                                        case FLOAT -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_FLOAT", AutoLinker.CD_ValueLayout_OfFloat);
                                        case DOUBLE -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_DOUBLE", AutoLinker.CD_ValueLayout_OfDouble);
                                        default -> throw invalidArgType(this, argType);
                                    }
                                    cb.aload(varIdx);
                                    cb.invokeinterface(AutoLinker.CD_SegmentAllocator, "allocateFrom", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, AutoLinker.CD_ValueLayout, arrayType));
                                    if (dir.out()) {
                                        cb.dup();
                                        copySlot = cb.allocateLocal(TypeKind.REFERENCE);
                                        cb.astore(copySlot);
                                    } else {
                                        copySlot = -1;
                                    }
                                } else {
                                    copySlot = -1;

                                }
                                if (dir.out()) {
                                    return xb -> {
                                        // copy back into the original array
                                        xb.aload(copySlot);
                                        xb.lconst_0();
                                        xb.aload(varIdx);
                                        xb.invokestatic(AutoLinker.CD_MemorySegment, "ofArray", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, arrayType), true);
                                        xb.lconst_0();
                                        xb.aload(varIdx);
                                        xb.arraylength();
                                        xb.i2l();
                                        xb.invokestatic(AutoLinker.CD_MemorySegment, "copy", MethodTypeDesc.of(ConstantDescs.CD_void, CD_MemorySegment, ConstantDescs.CD_long, CD_MemorySegment, ConstantDescs.CD_long, ConstantDescs.CD_long));
                                    };
                                }
                            }
                        } else {
                            // todo: other structure types and arrays of structure types
                            throw invalidArgType(this, argType);
                        }
                    } else if (argType.isPrimitive()) {
                        // pass the value by reference to a temp allocation
                        cb.aload(arenaVar);
                        TypeKind tk = TypeKind.from(argType);
                        switch (tk) {
                            case BYTE -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_BYTE", AutoLinker.CD_ValueLayout_OfByte);
                            case CHAR -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_CHAR", AutoLinker.CD_ValueLayout_OfChar);
                            case SHORT -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_SHORT", AutoLinker.CD_ValueLayout_OfShort);
                            case INT -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_INT", AutoLinker.CD_ValueLayout_OfInt);
                            case LONG -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_LONG", AutoLinker.CD_ValueLayout_OfLong);
                            case FLOAT -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_FLOAT", AutoLinker.CD_ValueLayout_OfFloat);
                            case DOUBLE -> cb.getstatic(AutoLinker.CD_ValueLayout, "JAVA_DOUBLE", AutoLinker.CD_ValueLayout_OfDouble);
                            default -> throw invalidArgType(this, argType);
                        }
                        cb.loadLocal(tk, varIdx);
                        cb.invokeinterface(AutoLinker.CD_SegmentAllocator, "allocateFrom", MethodTypeDesc.of(CD_MemorySegment, CD_ValueLayout, argType.describeConstable().orElseThrow()));
                    } else {
                        switch (argType.getName()) {
                            case "java.lang.foreign.MemorySegment" -> cb.aload(varIdx);
                            case "java.nio.ByteBuffer" -> {
                                cb.aload(varIdx);
                                cb.invokestatic(AutoLinker.CD_MemorySegment, "ofBuffer", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, AutoLinker.CD_Buffer), true);
                            }
                            case "java.lang.String" -> {
                                if (heap) {
                                    // use a heap buffer to avoid native allocations
                                    cb.aload(varIdx);
                                    cb.getstatic(AutoLinker.CD_StandardCharsets, "UTF_8", AutoLinker.CD_Charset);
                                    cb.invokevirtual(ConstantDescs.CD_String, "getBytes", MethodTypeDesc.of(ConstantDescs.CD_byte.arrayType(), AutoLinker.CD_Charset));
                                    cb.invokestatic(AutoLinker.CD_MemorySegment, "ofArray", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, ConstantDescs.CD_byte.arrayType()), true);
                                } else {
                                    cb.aload(arenaVar);
                                    cb.aload(varIdx);
                                    cb.getstatic(AutoLinker.CD_StandardCharsets, "UTF_8", AutoLinker.CD_Charset);
                                    cb.invokeinterface(AutoLinker.CD_SegmentAllocator, "allocateFrom", MethodTypeDesc.of(AutoLinker.CD_MemorySegment, ConstantDescs.CD_String, AutoLinker.CD_Charset));
                                }
                            }
                            // todo: other structure types
                            default -> throw invalidArgType(this, argType);
                        }
                    }
                }
                default -> throw invalidArgType(this, argType);
            }
            return null;
        }

        public boolean needsArena(final Class<?> argType, final boolean heap) {
            return argType == String.class || argType.isArray() && ! heap || argType.isPrimitive();
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case REFERENCE -> {
                    switch (returnType.getName()) {
                        case "java.lang.foreign.MemorySegment" -> {}
                        case "java.nio.ByteBuffer" -> cb.invokestatic(AutoLinker.CD_MemorySegment, "asByteBuffer", MethodTypeDesc.of(AutoLinker.CD_ByteBuffer), true);
                        default -> throw invalidReturnType(this, returnType);
                    }
                }
                case VOID -> cb.pop();
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
    BOOL {
        public Class<?> carrier() {
            return boolean.class;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            switch (TypeKind.from(argType)) {
                case BOOLEAN -> cb.iload(varIdx);
                default -> throw invalidArgType(this, argType);
            }
            return null;
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            switch (TypeKind.from(returnType)) {
                case BOOLEAN -> cb.ifThenElse(CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                case VOID -> cb.pop();
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
    VOID {
        public Class<?> carrier() {
            return void.class;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            // drop this argument
            return null;
        }

        public void emitReturn(final CodeBuilder cb, final Class<?> returnType) {
            TypeKind tk = TypeKind.from(returnType);
            switch (tk) {
                case BYTE, SHORT, INT, CHAR, BOOLEAN -> cb.iconst_0();
                case FLOAT -> cb.fconst_0();
                case DOUBLE -> cb.dconst_0();
                case LONG -> cb.lconst_0();
                case REFERENCE -> cb.aconst_null();
                case VOID -> {}
            }
        }

        public boolean hasLayout() {
            return false;
        }
    },
    /**
     * A marker indicating the start of variable arguments.
     */
    START_VA {
        public Class<?> carrier() {
            return void.class;
        }

        public void applyOption(final CodeBuilder cb, final int argIdx, final Parameter param) {
            pushInt(cb, argIdx);
            cb.invokestatic(CD_Linker_Option, "firstVariadicArg", MTD_Linker_Option_int, true);
        }

        public boolean hasOption() {
            return true;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            // no arguments consumed
            return null;
        }

        public boolean hasLayout() {
            return false;
        }

        public boolean consumeArgument() {
            return false;
        }
    },
    /**
     * Captured call state.
     */
    CAPTURE {
        public Class<?> carrier() {
            return LazyLink.MEMORY_SEGMENT;
        }

        public void applyOption(final CodeBuilder cb, final int argIdx, final Parameter param) {
            if (argIdx != 0) {
                throw new IllegalArgumentException("Capture must be first argument");
            }
            Link.capture captureAnn = param.getAnnotation(Link.capture.class);
            String[] capture = captureAnn.value();
            pushInt(cb, capture.length);
            cb.anewarray(ConstantDescs.CD_String);
            int capIdx = 0;
            for (String cap : capture) {
                cb.dup();
                pushInt(cb, capIdx++);
                cb.ldc(cap);
                cb.aastore();
            }
            cb.invokestatic(CD_Linker_Option, "captureCallState", MTD_Linker_Option_String_array, true);
        }

        public boolean hasLayout() {
            return false;
        }

        public boolean hasOption() {
            return true;
        }

        public Consumer<CodeBuilder> applyArgument(final CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
            cb.aload(varIdx);
            return null;
        }
    },
    ;

    private static IllegalArgumentException invalidArgType(final Transformation xform, final Class<?> argType) {
        return new IllegalArgumentException("Cannot use transformation " + xform + " with argument type " + argType);
    }

    private static IllegalArgumentException invalidReturnType(final Transformation xform, final Class<?> argType) {
        return new IllegalArgumentException("Cannot use transformation " + xform + " with return type " + argType);
    }

    Transformation() {}

    static Transformation forJavaType(final Class<?> type) {
        return switch (TypeKind.from(type)) {
            case BYTE -> S8;
            case SHORT -> S16;
            case INT -> S32;
            case FLOAT -> F32;
            case LONG -> S64;
            case DOUBLE -> F64;
            case REFERENCE -> {
                if (type.isArray() || type == LazyLink.MEMORY_SEGMENT || type == String.class) {
                    yield PTR;
                } else if (NativeEnum.class.isAssignableFrom(type)) {
                    yield S32;
                } else {
                    throw new IllegalArgumentException("No conversion for Java type " + type);
                }
            }
            case CHAR -> U16;
            case BOOLEAN -> BOOL;
            case VOID -> VOID;
        };
    }

    /**
     * {@return the native-side value layout carrier for this transformation}
     */
    public abstract Class<?> carrier();

    /**
     * Apply the argument for this transformation into a call which is being built.
     *
     * @param cb       the code builder (not {@code null})
     * @param varIdx   the argument's variable slot index
     * @param argType  the type of the argument (not {@code null})
     * @param heap     {@code true} if heap access is available, or {@code false} if it is not
     * @param arenaVar the variable index of the allocation arena, or {@code -1} if there is none
     * @param dir
     * @return a post-call cleanup action to take, or {@code null} if none is needed
     */
    public Consumer<CodeBuilder> applyArgument(CodeBuilder cb, final int varIdx, final Class<?> argType, final boolean heap, final int arenaVar, final Direction dir) {
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
     * @param param
     */
    public void applyOption(CodeBuilder cb, final int argIdx, final Parameter param) {
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

    /**
     * {@return <code>true</code> if this transformation requires an allocation arena}
     */
    public boolean needsArena(final Class<?> argType, boolean heap) {
        return false;
    }
}
