module io.github.dmlloyd.autolinker {
    requires java.base;

    requires io.smallrye.common.constraint;
    requires io.smallrye.common.cpu;
    requires io.smallrye.common.os;

    requires io.github.dmlloyd.classfile;

    exports io.github.dmlloyd.autolinker;
}
