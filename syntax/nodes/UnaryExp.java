package syntax.nodes;

import lexical.Token;

public class UnaryExp extends ASTnode {

    public PrimaryExp primaryExp;
    public Ident ident;
    public FuncRParams funcRParams;
    public Token unaryOp;
    public UnaryExp unaryExp;
}
