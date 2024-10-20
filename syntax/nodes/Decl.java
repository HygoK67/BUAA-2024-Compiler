package syntax.nodes;

import java.util.ArrayList;

public class Decl extends ASTnode {

    public boolean isConst;

    public Btype btype;

    public ArrayList<VarConstDef> varConstDefs = new ArrayList<>();

}
