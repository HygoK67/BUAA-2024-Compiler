package syntax.nodes;

import java.util.ArrayList;

public class Block extends ASTnode {
    public ArrayList<BlockItem> blockItems = new ArrayList<>();
    public int lastRBraceLineNum;
}
