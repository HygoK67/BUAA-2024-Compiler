package symbol;

public class ValueType implements Type {
    public enum BasicType {
        CHR, INT
    } // 当前符号的基本类型 (int 还是 char)

    public BasicType basicType;
    public boolean isConst = false; // 默认不是常数，可以修改

    public Integer arrayLength = null; //默认为 null，代表当前符号不是数组

    @Override
    public String toString() {
        StringBuilder typeString = new StringBuilder();
        if (isConst) {
            typeString.append("Const");
        }
        if (basicType == BasicType.CHR) {
            typeString.append("Char");
        }
        else {
            typeString.append("Int");
        }
        if (arrayLength != null) {
            typeString.append("Array");
        }
        return typeString.toString();
    }
}
