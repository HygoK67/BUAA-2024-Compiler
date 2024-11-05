package symbol;

import java.util.ArrayList;

public class Symbol {
    public String symbolName;
    public Type symbolType;
    public int scopeNum;
    public int defLineNum; // 符号的第一次定义在第几行
    public ArrayList<Integer> constValues = new ArrayList<>(); // 可能存在的若干个常量值（考虑到常量数组的情况）

    @Override
    public String toString() {
        return scopeNum + " " + symbolName + " " + symbolType.toString();
    }
}
