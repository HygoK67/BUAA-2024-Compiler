package symbol;

import java.util.ArrayList;
import java.util.HashMap;

public class FunctionType implements Type {
    public enum ReturnType {
        CHR, INT, VOID
    }

    public ReturnType returnType;
    public ArrayList<String> paramNames = new ArrayList<>(); // 参数名字列表
    public ArrayList<ValueType> paramTypes = new ArrayList<>(); // 参数类型列表

    @Override
    public String toString() {
        if (returnType == ReturnType.CHR) {
            return "CharFunc";
        }
        else if (returnType == ReturnType.INT) {
            return "IntFunc";
        }
        else {
            return "VoidFunc";
        }
    }
}
