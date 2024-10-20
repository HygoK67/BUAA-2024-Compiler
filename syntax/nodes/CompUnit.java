package syntax.nodes;

import com.sun.tools.javac.Main;

import java.util.ArrayList;

public class CompUnit extends ASTnode {

    public ArrayList<Decl> decls;

    public ArrayList<FuncDef> funcDefs;

    public FuncDef mainFuncDef;

    public CompUnit() {
        this.decls = new ArrayList<>();
        this.funcDefs = new ArrayList<>();
        this.mainFuncDef = null;
    }
}
