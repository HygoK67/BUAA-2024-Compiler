package syntax.nodes;

public class Ident extends ASTnode {
    public String name;

    public Ident(String name) {
        this.name = name;
    }
}
