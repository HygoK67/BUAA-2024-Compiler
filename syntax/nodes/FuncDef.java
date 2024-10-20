package syntax.nodes;

import lexical.Token;

public class FuncDef extends ASTnode{

    public boolean isMain;
    public Ident ident;
    public Token funcType;
    public FuncFParams funcFParams;
    public Block block;

}
