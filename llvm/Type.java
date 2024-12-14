package llvm;

public class Type {
    public enum BasicType {
        i32, i8, i1, vo, i32ptr, i8ptr, i32arr, i8arr, i32arrptr, i8arrptr
    }
    public BasicType basicType;
    public Integer arrayLength;

    public Type(BasicType basicType, Integer arrayLength) {
        this.basicType = basicType;
        this.arrayLength = arrayLength;
    }

    public static Type i32() {
        return new Type(BasicType.i32, null);
    }

    public static Type i1() {
        return new Type(BasicType.i1, null);
    }

    public static Type i8() {
        return new Type(BasicType.i8, null);
    }

    public static Type vo() {
        return new Type(BasicType.vo, null);
    }

    public static Type i32ptr() {
        return new Type(BasicType.i32ptr, null);
    }

    public static Type i8ptr() {
        return new Type(BasicType.i8ptr, null);
    }

    @Override
    public String toString() {
        switch (basicType) {
            case i32: return "i32";
            case i8: return "i8";
            case vo: return "void";
            case i32ptr: return "i32*";
            case i8ptr: return "i8*";
            case i32arr: return "[" + arrayLength + " x i32]";
            case i8arr: return "[" + arrayLength + " x i8]";
            case i32arrptr: return "[" + arrayLength + " x i32]*";
            case i8arrptr: return "[" + arrayLength + " x i8]*";
        }
        return "";
    }
}
