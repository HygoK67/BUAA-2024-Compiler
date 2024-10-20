package syntax.nodes;

import lexical.Token;

public class BiOperandExp extends ASTnode { // 两个操作数的表达式，如果没有两个操作数，那么 operator 为 null,rightElement 也为 null
    public ASTnode leftElement;
    public ASTnode rightElement;
    public Token operator;
}
