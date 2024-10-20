package syntax.nodes;

import lexical.Token;

public class PrimaryExp extends ASTnode {
    public BiOperandExp exp; // 对应 '(' Exp ')' 的情况
    public Lval lval;
    public Token number;
    public Token character;
}
