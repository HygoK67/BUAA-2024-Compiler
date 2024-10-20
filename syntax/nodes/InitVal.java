package syntax.nodes;

import java.util.ArrayList;

public class InitVal extends ASTnode { // 变量、常量定义初始值

    public boolean isConst; // 是 constInitVal 还是普通的 initVal

    public String stringConst = null; // 字符串常量作为初始值

    public ArrayList<BiOperandExp> expArray = new ArrayList<>(); // 若干个表达式作为初始值

}
