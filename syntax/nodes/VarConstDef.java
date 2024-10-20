package syntax.nodes;

import java.util.ArrayList;

public class VarConstDef extends ASTnode {

    public boolean isConst; // 是 constDef 还是 VarDef

    public Ident ident; // 标识符

    public BiOperandExp dimensionConstExp; // 数组纬度信息，是一个常量表达式，如果为 null 代表此定义不是数组

    public InitVal initVal;

}
