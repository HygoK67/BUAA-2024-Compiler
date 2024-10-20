package syntax.nodes;

import lexical.Token;

import java.util.ArrayList;

public class Stmt {

    public int caseNum; // 当前语句属于第几种情况

    public Lval lval0; // 情况 0 : Stmt -> LVal '=' Exp ';'
    public BiOperandExp exp0;

    public BiOperandExp exp1; // 情况 1 : Stmt -> [Exp] ';'

    public Block block2; // 情况 2 : Stmt -> Block

    public BiOperandExp condExp3; // 情况 3 : Stmt -> 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
    public Stmt ifStmtIf3;
    public Stmt ifStmtElse3;

    public Stmt forStmtA4; // 情况 4 : Stmt -> 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt
    public BiOperandExp condExp4;
    public Stmt forStmtB4;
    public Stmt stmt4;

    // 情况 5 : Stmt -> break ';'

    // 情况 6 : Stmt -> continue ';'

    public BiOperandExp returnExp7; // 情况 7 : Stmt -> 'return' [Exp] ';'

    public Lval lval8; // 情况 8 : Stmt -> LVal '=' 'getint''('')'';'

    public Lval lval9; // 情况 9 : Stmt -> LVal '=' 'getchar''('')'';'

    public Token stringConst10; // 情况 10 : Stmt -> 'printf''('StringConst {','Exp}')'';'
    public ArrayList<BiOperandExp> exps10 = new ArrayList<>();

}
