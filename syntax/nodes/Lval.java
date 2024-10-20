package syntax.nodes;

public class Lval extends ASTnode {
    public Ident ident; // 标识符
    public BiOperandExp exp; // 数组纬度表达式（如果存在的话，不存在就是 null）
}