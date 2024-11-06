package syntax;

import lexical.Lexer;
import lexical.Token;
import program.ProgramException;
import syntax.nodes.*;

import java.io.FileWriter;
import java.util.ArrayList;

public class Parser {

    private boolean debugFlag;
    private FileWriter debugWriter;

    private Lexer lexer;

    public Parser(Lexer lexer, boolean debugFlag, FileWriter debugWriter) {
        this.lexer = lexer;
        this.debugFlag = debugFlag;
        this.debugWriter = debugWriter;
    }

    public CompUnit parseCompUnit() throws Exception {
        CompUnit compUnit = new CompUnit();
        compUnit.lineNum = lexer.getCurrentToken().getLine();
        while (lexer.getCurrentToken().getType() != Token.TokenType.EOF) {
            if (lexer.getCurrentToken().getType() == Token.TokenType.CONSTTK) { // 只能是常量定义
                compUnit.decls.add(parseConstDecl());
            }
            else if (lexer.getCurrentToken().getType() == Token.TokenType.VOIDTK) { // 只能是函数定义
                compUnit.funcDefs.add(parseFuncDef());
            }
            else if (lexer.getCurrentToken().getType() == Token.TokenType.INTTK) { // 有可能为变量定义，函数定义或者主函数定义
                if (lexer.tokenPreRead(1).getType() == Token.TokenType.MAINTK && lexer.tokenPreRead(2).getType() == Token.TokenType.LPARENT) {
                    compUnit.mainFuncDef = parseMainFuncDef();
                    break;
                }
                else if (lexer.tokenPreRead(1).getType() == Token.TokenType.IDENFR && lexer.tokenPreRead(2).getType() == Token.TokenType.LPARENT) {
                    compUnit.funcDefs.add(parseFuncDef());
                }
                else if (lexer.tokenPreRead(1).getType() == Token.TokenType.IDENFR) {
                    compUnit.decls.add(parseVarDecl());
                }
                else {
                    error("无法解析的CompUnit内部成分！，出现在 " + this.lexer.getCurrentToken().getLine() + " 行");
                }
            }
            else if (lexer.getCurrentToken().getType() == Token.TokenType.CHARTK) { //有可能为变量定义或函数定义
                if (lexer.tokenPreRead(1).getType() == Token.TokenType.IDENFR && lexer.tokenPreRead(2).getType() == Token.TokenType.LPARENT) {
                    compUnit.funcDefs.add(parseFuncDef());
                }
                else if (lexer.tokenPreRead(1).getType() == Token.TokenType.IDENFR) {
                    compUnit.decls.add(parseVarDecl());
                }
                else {
                    error("无法解析的CompUnit内部成分！，出现在 " + this.lexer.getCurrentToken().getLine() + " 行");
                }
            }
            else {
                error("无法解析的CompUnit内部成分！，出现在 " + this.lexer.getCurrentToken().getLine() + " 行");
            }
        }
        if (debugFlag) { debugWriter.write("<CompUnit>\n"); }
        return compUnit;
    }

    public FuncDef parseMainFuncDef() throws Exception {
        FuncDef mainFuncDef = new FuncDef();
        mainFuncDef.lineNum = lexer.getCurrentToken().getLine();
        mainFuncDef.isMain = true;
        skipSign(Token.TokenType.INTTK, null);
        skipSign(Token.TokenType.MAINTK, null);
        skipSign(Token.TokenType.LPARENT, null);
        skipSign(Token.TokenType.RPARENT, 'j');
        mainFuncDef.block = parseBlock();
        if (debugFlag) { debugWriter.write("<MainFuncDef>\n"); }
        return mainFuncDef;
    }

    public Token parseFuncType() throws Exception {
        Token funcType = null;
        if (lexer.getCurrentToken().getType() == Token.TokenType.VOIDTK // 解析函数类型
                || lexer.getCurrentToken().getType() == Token.TokenType.INTTK
                || lexer.getCurrentToken().getType() == Token.TokenType.CHARTK) {
            funcType = lexer.getCurrentToken();
            lexer.nextToken();
        }
        else {
            error("无法解析的函数类型");
        }
        if (debugFlag) { debugWriter.write("<FuncType>\n"); }
        return funcType;
    }

    public FuncDef parseFuncDef() throws Exception {
        FuncDef funcDef = new FuncDef();
        funcDef.lineNum = lexer.getCurrentToken().getLine();
        funcDef.isMain = false;
        funcDef.funcType = parseFuncType();
        if (lexer.getCurrentToken().getType() == Token.TokenType.IDENFR) { // 解析函数标识符
            funcDef.ident = new Ident(lexer.getCurrentToken().getToken());
            lexer.nextToken();
        }
        else {
            error("监测到非法的函数定义");
        }
        skipSign(Token.TokenType.LPARENT, null);
        if (lexer.getCurrentToken().getType() == Token.TokenType.LBRACE) {
            skipSign(Token.TokenType.RPARENT, 'j');
        }
        else if (lexer.getCurrentToken().getType() != Token.TokenType.RPARENT) {
            funcDef.funcFParams = parseFuncFParams();
            skipSign(Token.TokenType.RPARENT, 'j');
        }
        else {
            lexer.nextToken();
        }
        funcDef.block = parseBlock();
        if (debugFlag) { debugWriter.write("<FuncDef>\n"); }
        return funcDef;
    }

    public FuncFParams parseFuncFParams() throws Exception {
        FuncFParams funcFParams = new FuncFParams();
        funcFParams.lineNum = lexer.getCurrentToken().getLine();
        funcFParams.funcFParams.add(parseFuncFParam());
        while (lexer.getCurrentToken().getType() == Token.TokenType.COMMA) {
            lexer.nextToken();
            funcFParams.funcFParams.add(parseFuncFParam());
        }
        if (debugFlag) { debugWriter.write("<FuncFParams>\n"); }
        return funcFParams;
    }

    public FuncFParam parseFuncFParam() throws Exception {
        FuncFParam funcFParam = new FuncFParam();
        funcFParam.lineNum = lexer.getCurrentToken().getLine();
        if (lexer.getCurrentToken().getType() == Token.TokenType.INTTK) {
            funcFParam.btype = Btype.INT;
            lexer.nextToken();
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.CHARTK) {
            funcFParam.btype = Btype.CHAR;
            lexer.nextToken();
        }
        else {
            error("解析函数定义参数出错！");
        }
        if (lexer.getCurrentToken().getType() == Token.TokenType.IDENFR) {
            funcFParam.ident = new Ident(lexer.getCurrentToken().getToken());
            lexer.nextToken();
        }
        else {
            error("解析函数定义参数出错！");
        }
        if (lexer.getCurrentToken().getType() == Token.TokenType.LBRACK) {
            funcFParam.isArray = true;
            lexer.nextToken();
            skipSign(Token.TokenType.RBRACK, 'k');
        }
        if (debugFlag) { debugWriter.write("<FuncFParam>\n"); }
        return funcFParam;
    }

    public Block parseBlock() throws Exception {
        Block block = new Block();
        block.lineNum = lexer.getCurrentToken().getLine();
        skipSign(Token.TokenType.LBRACE, null);
        while (lexer.getCurrentToken().getType() != Token.TokenType.RBRACE) {
            block.blockItems.add(parseBlockItem());
        }
        block.lastRBraceLineNum = lexer.getCurrentToken().getLine(); // 记录一个块最后的 } 符号所在位置
        lexer.nextToken();
        if (debugFlag) { debugWriter.write("<Block>\n"); }
        return block;
    }

    public BlockItem parseBlockItem() throws Exception {
        BlockItem blockItem = new BlockItem();
        blockItem.lineNum = lexer.getCurrentToken().getLine();
        if (lexer.getCurrentToken().getType() == Token.TokenType.INTTK
            || lexer.getCurrentToken().getType() == Token.TokenType.CHARTK) {
            blockItem.decl = parseVarDecl();
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.CONSTTK) {
            blockItem.decl = parseConstDecl();
        }
        else {
            blockItem.stmt = parseStmt();
        }
//        if (debugFlag) { debugWriter.write("<BlockItem>\n"); }
        return blockItem;
    }

    public Stmt parseStmt() throws Exception {
        Stmt stmt = new Stmt();
        stmt.lineNum = lexer.getCurrentToken().getLine();
        if (lexer.getCurrentToken().getType() == Token.TokenType.LBRACE) { // 情况2
            stmt.caseNum = 2;
            stmt.block2 = parseBlock();
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.IFTK) { // 情况3
            stmt.caseNum = 3;
            lexer.nextToken();
            skipSign(Token.TokenType.LPARENT, null);
            stmt.condExp3 = parseCond();
            skipSign(Token.TokenType.RPARENT, 'j');
            stmt.ifStmtIf3 = parseStmt();
            if (lexer.getCurrentToken().getType() == Token.TokenType.ELSETK) {
                lexer.nextToken();
                stmt.ifStmtElse3 = parseStmt();
            }
        } else if (lexer.getCurrentToken().getType() == Token.TokenType.FORTK) { // 情况4
            lexer.nextToken();
            skipSign(Token.TokenType.LPARENT, null);
            stmt.caseNum = 4;
            if (lexer.getCurrentToken().getType() == Token.TokenType.SEMICN) {
                lexer.nextToken();
            }
            else {
                stmt.forStmtA4 = parseForStmt();
                skipSign(Token.TokenType.SEMICN, 'i');
            }
            if (lexer.getCurrentToken().getType() == Token.TokenType.SEMICN) {
                lexer.nextToken();
            }
            else {
                if (lexer.getCurrentToken().getType() == Token.TokenType.RPARENT) {
                    skipSign(Token.TokenType.SEMICN, 'i');
                }
                else {
                    stmt.condExp4 = parseCond();
                    skipSign(Token.TokenType.SEMICN, 'i');
                }
            }
            if (lexer.getCurrentToken().getType() == Token.TokenType.RPARENT) {
                lexer.nextToken();
            }
            else {
                if (lexer.getCurrentToken().getType() == Token.TokenType.LBRACE) {
                    skipSign(Token.TokenType.RPARENT, 'j');
                }
                else {
                    stmt.forStmtB4 = parseForStmt();
                    skipSign(Token.TokenType.RPARENT, 'j');
                }
            }
            stmt.stmt4 = parseStmt();
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.BREAKTK) {
            stmt.caseNum = 5;
            lexer.nextToken();
            skipSign(Token.TokenType.SEMICN, 'i');
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.CONTINUETK) {
            stmt.caseNum = 6;
            lexer.nextToken();
            skipSign(Token.TokenType.SEMICN, 'i');
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.RETURNTK) {
            stmt.caseNum = 7;
            lexer.nextToken();
            if (lexer.getCurrentToken().getType() == Token.TokenType.SEMICN) {
                lexer.nextToken();
            }
            else {
                try {
                    stmt.returnExp7 = parseExp();
                }
                catch (Exception e) {

                }
                skipSign(Token.TokenType.SEMICN, 'i');
            }
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.PRINTFTK) {
            stmt.caseNum = 10;
            lexer.nextToken();
            skipSign(Token.TokenType.LPARENT, null);
            if (lexer.getCurrentToken().getType() == Token.TokenType.STRCON) {
                stmt.stringConst10 = lexer.getCurrentToken();
                lexer.nextToken();
            }
            else {
                error("无法解析 printf 函数");
            }
            while (lexer.getCurrentToken().getType() == Token.TokenType.COMMA) {
                lexer.nextToken();
                stmt.exps10.add(parseExp());
            }
            skipSign(Token.TokenType.RPARENT, 'j');
            skipSign(Token.TokenType.SEMICN, 'i');
        }
        else { // 情况0，情况1，情况8和情况9
            boolean assignFlag = false; //  是否出现赋值符号，如果出现则不可能是情况 1
            int crtLinNum = lexer.getCurrentToken().getLine();
            for (int i = 0;;i++) {
                if (lexer.tokenPreRead(i).getType() == Token.TokenType.ASSIGN) {
                    assignFlag = true;
                    break;
                }
                if (lexer.tokenPreRead(i).getType() == Token.TokenType.SEMICN) {
                    break;
                }
                if (lexer.tokenPreRead(i).getLine() > crtLinNum) {
                    break;
                }
            }
            if (assignFlag) {
                Lval lval = new Lval();
                lval = parseLval();
                skipSign(Token.TokenType.ASSIGN, null);
                if (lexer.getCurrentToken().getType() == Token.TokenType.GETINTTK) {
                    lexer.nextToken();
                    stmt.caseNum = 8;
                    stmt.lval8 = lval;
                    skipSign(Token.TokenType.LPARENT, null);
                    skipSign(Token.TokenType.RPARENT, 'j');
                    skipSign(Token.TokenType.SEMICN, 'i');
                }
                else if (lexer.getCurrentToken().getType() == Token.TokenType.GETCHARTK) {
                    lexer.nextToken();
                    stmt.caseNum = 9;
                    stmt.lval9 = lval;
                    skipSign(Token.TokenType.LPARENT, null);
                    skipSign(Token.TokenType.RPARENT, 'j');
                    skipSign(Token.TokenType.SEMICN, 'i');
                }
                else {
                    stmt.caseNum = 0;
                    stmt.lval0 = lval;
                    stmt.exp0 = parseExp();
                    skipSign(Token.TokenType.SEMICN, 'i');
                }
            }
            else {
                // 没有赋值符号，那么就是情况1（直接一个 exp 表达式）
                stmt.caseNum = 1;
                if (lexer.getCurrentToken().getType() == Token.TokenType.SEMICN) {
                    lexer.nextToken();
                }
                else {
                    stmt.exp1 = parseExp();
                    skipSign(Token.TokenType.SEMICN, 'i');
                }
            }
        }
        if (debugFlag) { debugWriter.write("<Stmt>\n"); }
        return stmt;
    }

    public Stmt parseForStmt() throws Exception {
        Stmt stmt = new Stmt();
        stmt.lineNum = lexer.getCurrentToken().getLine();
        stmt.caseNum = 0;
        stmt.lval0 = parseLval();
        skipSign(Token.TokenType.ASSIGN, null);
        stmt.exp0 = parseExp();
        if (debugFlag) { debugWriter.write("<ForStmt>\n"); }
        return stmt;
    }

    public BiOperandExp parseCond() throws Exception {
        BiOperandExp biOperandExp = parseLOrExp();
        if (debugFlag) { debugWriter.write("<Cond>\n"); }
        return biOperandExp;
    }

    public BiOperandExp parseLOrExp() throws Exception {
        BiOperandExp biOperandExp = new BiOperandExp();
        biOperandExp.lineNum = lexer.getCurrentToken().getLine();
        biOperandExp.leftElement = parseLAndExp();
        while (lexer.getCurrentToken().getType() == Token.TokenType.OR) {
            BiOperandExp newBioperandExp = new BiOperandExp();
            newBioperandExp.lineNum = lexer.getCurrentToken().getLine();
            newBioperandExp.leftElement = biOperandExp;
            newBioperandExp.operator = lexer.getCurrentToken();
            if (debugFlag) { debugWriter.write("<LOrExp>\n"); }
            lexer.nextToken();
            newBioperandExp.rightElement = parseLAndExp();
            biOperandExp = newBioperandExp;
        }
        if (debugFlag) { debugWriter.write("<LOrExp>\n"); }
        return biOperandExp;
    }

    public BiOperandExp parseLAndExp() throws Exception {
        BiOperandExp biOperandExp = new BiOperandExp();
        biOperandExp.lineNum = lexer.getCurrentToken().getLine();
        biOperandExp.leftElement = parseEqExp();
        while (lexer.getCurrentToken().getType() == Token.TokenType.AND) {
            BiOperandExp newBioperandExp = new BiOperandExp();
            newBioperandExp.lineNum = lexer.getCurrentToken().getLine();
            newBioperandExp.leftElement = biOperandExp;
            newBioperandExp.operator = lexer.getCurrentToken();
            if (debugFlag) { debugWriter.write("<LAndExp>\n"); }
            lexer.nextToken();
            newBioperandExp.rightElement = parseEqExp();
            biOperandExp = newBioperandExp;
        }
        if (debugFlag) { debugWriter.write("<LAndExp>\n"); }
        return biOperandExp;
    }

    public BiOperandExp parseEqExp() throws Exception {
        BiOperandExp biOperandExp = new BiOperandExp();
        biOperandExp.lineNum = lexer.getCurrentToken().getLine();
        biOperandExp.leftElement = parseRelExp();
        while (lexer.getCurrentToken().getType() == Token.TokenType.EQL
                || lexer.getCurrentToken().getType() == Token.TokenType.NEQ) {
            BiOperandExp newBioperandExp = new BiOperandExp();
            newBioperandExp.lineNum = lexer.getCurrentToken().getLine();
            newBioperandExp.leftElement = biOperandExp;
            newBioperandExp.operator = lexer.getCurrentToken();
            if (debugFlag) { debugWriter.write("<EqExp>\n"); }
            lexer.nextToken();
            newBioperandExp.rightElement = parseRelExp();
            biOperandExp = newBioperandExp;
        }
        if (debugFlag) { debugWriter.write("<EqExp>\n"); }
        return biOperandExp;
    }

    public BiOperandExp parseRelExp() throws Exception {
        BiOperandExp biOperandExp = new BiOperandExp();
        biOperandExp.lineNum = lexer.getCurrentToken().getLine();
        biOperandExp.leftElement = parseAddExp();
        while (lexer.getCurrentToken().getType() == Token.TokenType.LSS
            || lexer.getCurrentToken().getType() == Token.TokenType.GRE
            || lexer.getCurrentToken().getType() == Token.TokenType.LEQ
            || lexer.getCurrentToken().getType() == Token.TokenType.GEQ) {
            BiOperandExp newBioperandExp = new BiOperandExp();
            newBioperandExp.lineNum = lexer.getCurrentToken().getLine();
            newBioperandExp.leftElement = biOperandExp;
            newBioperandExp.operator = lexer.getCurrentToken();
            if (debugFlag) { debugWriter.write("<RelExp>\n"); }
            lexer.nextToken();
            newBioperandExp.rightElement = parseAddExp();
            biOperandExp = newBioperandExp;
        }
        if (debugFlag) { debugWriter.write("<RelExp>\n"); }
        return biOperandExp;
    }

    public Decl parseConstDecl() throws Exception {
        Decl decl = new Decl();
        decl.lineNum = lexer.getCurrentToken().getLine();
        decl.isConst = true;
        skipSign(Token.TokenType.CONSTTK, null); // 保证第一个符号是 const
        if (lexer.getCurrentToken().getType() == Token.TokenType.INTTK) {
            decl.btype = Btype.INT;
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.CHARTK) {
            decl.btype = Btype.CHAR;
        }
        else {
            error("未检测到常量定义类型 Btype");
        }
        lexer.nextToken();
        decl.varConstDefs.add(parseConstDef());
        while (true) {
            if (lexer.getCurrentToken().getType() == Token.TokenType.COMMA) { // 检测到逗号就继续解析
                lexer.nextToken();
                decl.varConstDefs.add(parseConstDef());
            }
            else {
                break;
            }
        }
        skipSign(Token.TokenType.SEMICN, 'i'); // 保证有 ;
        if (debugFlag) { debugWriter.write("<ConstDecl>\n"); }
        return decl;
    }

    public Decl parseVarDecl() throws Exception {
        Decl decl = new Decl();
        decl.lineNum = lexer.getCurrentToken().getLine();
        decl.isConst = false;
        if (lexer.getCurrentToken().getType() == Token.TokenType.INTTK) {
            decl.btype = Btype.INT;
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.CHARTK) {
            decl.btype = Btype.CHAR;
        }
        else {
            error("未检测到常量定义类型 Btype");
        }
        lexer.nextToken();
        decl.varConstDefs.add(parseVarDef());
        while (true) {
            if (lexer.getCurrentToken().getType() == Token.TokenType.COMMA) { // 检测到逗号就继续解析
                lexer.nextToken();
                decl.varConstDefs.add(parseVarDef());
            }
            else {
                break;
            }
        }
        skipSign(Token.TokenType.SEMICN, 'i'); // 保证有 ;
        if (debugFlag) { debugWriter.write("<VarDecl>\n"); }
        return decl;
    }

    public VarConstDef parseVarDef() throws Exception {
        VarConstDef varConstDef = new VarConstDef();
        varConstDef.lineNum = lexer.getCurrentToken().getLine();
        varConstDef.isConst = false;
        if (lexer.getCurrentToken().getType() != Token.TokenType.IDENFR) {
            error("解析 varDef 时遇到了错误");
        }
        varConstDef.ident = new Ident(lexer.getCurrentToken().getToken());
        lexer.nextToken();
        if (lexer.getCurrentToken().getType() == Token.TokenType.LBRACK) {
            lexer.nextToken();
            varConstDef.dimensionConstExp = parseConstExp();
            skipSign(Token.TokenType.RBRACK, 'k');
        }
        if (lexer.getCurrentToken().getType() == Token.TokenType.ASSIGN) {
            lexer.nextToken();
            varConstDef.initVal = parseInitVal();
        }
        if (debugFlag) { debugWriter.write("<VarDef>\n"); }
        return varConstDef;
    }

    public VarConstDef parseConstDef() throws Exception {
        VarConstDef varConstDef = new VarConstDef();
        varConstDef.lineNum = lexer.getCurrentToken().getLine();
        varConstDef.isConst = true;
        if (lexer.getCurrentToken().getType() != Token.TokenType.IDENFR) {
            error("解析 constDef 时遇到了错误");
        }
        varConstDef.ident = new Ident(lexer.getCurrentToken().getToken());
        lexer.nextToken();
        // 检查有没有 [
        if (lexer.getCurrentToken().getType() == Token.TokenType.LBRACK) {
            lexer.nextToken();
            varConstDef.dimensionConstExp = parseConstExp();
            skipSign(Token.TokenType.RBRACK,'k');
        }
        skipSign(Token.TokenType.ASSIGN, null);
        varConstDef.initVal = parseConstInitVal();
        if (debugFlag) { debugWriter.write("<ConstDef>\n"); }
        return varConstDef;
    }

    public InitVal parseInitVal() throws Exception {
        InitVal initVal = new InitVal();
        initVal.lineNum = lexer.getCurrentToken().getLine();
        initVal.isConst = false;
        initVal.stringConst = null;
        if (lexer.getCurrentToken().getType() == Token.TokenType.STRCON) {
            initVal.stringConst = lexer.getCurrentToken().getToken();
            lexer.nextToken();
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.LBRACE) { // 一维数组初始值
            lexer.nextToken();
            if (lexer.getCurrentToken().getType() != Token.TokenType.RBRACE) {
                initVal.expArray.add(parseExp());
                while (true) {
                    if (lexer.getCurrentToken().getType() == Token.TokenType.COMMA) {
                        lexer.nextToken();
                        initVal.expArray.add(parseExp());
                    }
                    else {
                        break;
                    }
                }
                skipSign(Token.TokenType.RBRACE, null);
            }
            else {
                skipSign(Token.TokenType.RBRACE, null);
            }
        }
        else { // 单个表达式初始值
            initVal.expArray.add(parseExp());
        }
        if (debugFlag) { debugWriter.write("<InitVal>\n"); }
        return initVal;
    }

    public InitVal parseConstInitVal() throws Exception {
        InitVal initVal = new InitVal();
        initVal.lineNum = lexer.getCurrentToken().getLine();
        initVal.isConst = true;
        initVal.stringConst = null;
        if (lexer.getCurrentToken().getType() == Token.TokenType.STRCON) {
            initVal.stringConst = lexer.getCurrentToken().getToken();
            lexer.nextToken();
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.LBRACE) {
            lexer.nextToken();
            if (lexer.getCurrentToken().getType() != Token.TokenType.RBRACE) {
                // 解析 ConstExp
                initVal.expArray.add(parseConstExp());
                // 检查有没有 , 号
                while (true) {
                    if (lexer.getCurrentToken().getType() == Token.TokenType.COMMA) {
                        lexer.nextToken();
                        initVal.expArray.add(parseConstExp());
                    } else {
                        break;
                    }
                }
                skipSign(Token.TokenType.RBRACE, null);
            }
            else {
                skipSign(Token.TokenType.RBRACE, null);
            }
        }
        else {
            initVal.expArray.add(parseConstExp());
        }
        if (debugFlag) { debugWriter.write("<ConstInitVal>\n"); }
        return initVal;
    }

    public BiOperandExp parseExp() throws Exception {
        BiOperandExp biOperandExp = parseAddExp();
        if (debugFlag) { debugWriter.write("<Exp>\n"); }
        return biOperandExp;
    }

    public BiOperandExp parseAddExp() throws Exception {
        BiOperandExp biOperandExp = new BiOperandExp();
        biOperandExp.lineNum = lexer.getCurrentToken().getLine();
        biOperandExp.leftElement = parseMulExp();
        while (lexer.getCurrentToken().getType() == Token.TokenType.PLUS || lexer.getCurrentToken().getType() == Token.TokenType.MINU) {
            BiOperandExp newBiOperandExp = new BiOperandExp();
            newBiOperandExp.lineNum = lexer.getCurrentToken().getLine();
            newBiOperandExp.leftElement = biOperandExp;
            newBiOperandExp.operator = lexer.getCurrentToken();
            if (debugFlag) { debugWriter.write("<AddExp>\n"); }
            lexer.nextToken();
            newBiOperandExp.rightElement = parseMulExp();
            biOperandExp = newBiOperandExp;
        }
        if (debugFlag) { debugWriter.write("<AddExp>\n"); }
        return biOperandExp;
    }

    public BiOperandExp parseMulExp() throws Exception {
        BiOperandExp biOperandExp = new BiOperandExp();
        biOperandExp.lineNum = lexer.getCurrentToken().getLine();
        biOperandExp.leftElement = parseUnaryExp();
        while (lexer.getCurrentToken().getType() == Token.TokenType.MULT
                || lexer.getCurrentToken().getType() == Token.TokenType.DIV
                || lexer.getCurrentToken().getType() == Token.TokenType.MOD) {
            BiOperandExp newBiOperandExp = new BiOperandExp();
            newBiOperandExp.lineNum = lexer.getCurrentToken().getLine();
            newBiOperandExp.leftElement = biOperandExp;
            newBiOperandExp.operator = lexer.getCurrentToken();
            if (debugFlag) { debugWriter.write("<MulExp>\n"); }
            lexer.nextToken();
            newBiOperandExp.rightElement = parseUnaryExp();
            biOperandExp = newBiOperandExp;
        }
        if (debugFlag) { debugWriter.write("<MulExp>\n"); }
        return biOperandExp;
    }

    public UnaryExp parseUnaryExp() throws Exception {
        UnaryExp unaryExp = new UnaryExp();
        unaryExp.lineNum = lexer.getCurrentToken().getLine();
        if (lexer.getCurrentToken().getType() == Token.TokenType.IDENFR
                && lexer.tokenPreRead(1).getType() == Token.TokenType.LPARENT) {
            unaryExp.ident = new Ident(lexer.getCurrentToken().getToken());
            unaryExp.ident.lineNum = unaryExp.lineNum;
            lexer.nextToken();lexer.nextToken();
            if (lexer.getCurrentToken().getType() == Token.TokenType.RPARENT) {
                lexer.nextToken();
            }
            else if (lexer.getCurrentToken().getType() == Token.TokenType.SEMICN) {
                skipSign(Token.TokenType.RPARENT, 'j');
            }
            else {
                unaryExp.funcRParams = parseFuncRParams();
                skipSign(Token.TokenType.RPARENT, 'j');
            }
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.PLUS
                    || lexer.getCurrentToken().getType() == Token.TokenType.MINU
                    || lexer.getCurrentToken().getType() == Token.TokenType.NOT ) {
            unaryExp.unaryOp = lexer.getCurrentToken();
            lexer.nextToken();
            if (debugFlag) { debugWriter.write("<UnaryOp>\n"); }
            unaryExp.unaryExp = parseUnaryExp();
        }
        else {
            unaryExp.primaryExp = parsePrimaryExp();
        }
        if (debugFlag) { debugWriter.write("<UnaryExp>\n"); }
        return unaryExp;
    }

    public PrimaryExp parsePrimaryExp() throws Exception {
        PrimaryExp primaryExp = new PrimaryExp();
        primaryExp.lineNum = lexer.getCurrentToken().getLine();
        if (lexer.getCurrentToken().getType() == Token.TokenType.LPARENT) {
            lexer.nextToken();
            primaryExp.exp = parseExp();
            skipSign(Token.TokenType.RPARENT, 'j');
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.IDENFR) {
            primaryExp.lval = parseLval();
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.INTCON) {
            primaryExp.number = parseNumber();
        }
        else if (lexer.getCurrentToken().getType() == Token.TokenType.CHRCON) {
            primaryExp.character = parseCharacter();
        }
        else {
            error("无法解析的 primaryexp");
        }
        if (debugFlag) { debugWriter.write("<PrimaryExp>\n"); }
        return primaryExp;
    }

    public Token parseNumber() throws Exception {
        if (lexer.getCurrentToken().getType() != Token.TokenType.INTCON) {
            error("解析 Number 出错");
        }
        Token number = lexer.getCurrentToken();
        lexer.nextToken();
        if (debugFlag) { debugWriter.write("<Number>\n"); }
        return number;
    }

    public Token parseCharacter() throws Exception {
        if (lexer.getCurrentToken().getType() != Token.TokenType.CHRCON) {
            error("解析 Character 出错");
        }
        Token character = lexer.getCurrentToken();
        lexer.nextToken();
        if (debugFlag) { debugWriter.write("<Character>\n"); }
        return character;
    }

    public Lval parseLval() throws Exception {
        Lval lval = new Lval();
        lval.lineNum = lexer.getCurrentToken().getLine();
        if (lexer.getCurrentToken().getType() != Token.TokenType.IDENFR) {
            error("无法解析的左值表达式 Lval");
        }
        lval.ident = new Ident(lexer.getCurrentToken().getToken());
        lval.ident.lineNum = lval.lineNum;
        lexer.nextToken();
        if (lexer.getCurrentToken().getType() == Token.TokenType.LBRACK) {
            lexer.nextToken();
            lval.exp = parseExp();
            skipSign(Token.TokenType.RBRACK, 'k');
        }
        if (debugFlag) { debugWriter.write("<LVal>\n"); }
        return lval;
    }

    public FuncRParams parseFuncRParams() throws Exception {
        FuncRParams funcRParams = new FuncRParams();
        funcRParams.lineNum = lexer.getCurrentToken().getLine();
        funcRParams.exps.add(parseExp());
        while (true) {
            if (lexer.getCurrentToken().getType() == Token.TokenType.COMMA) {
                lexer.nextToken();
                funcRParams.exps.add(parseExp());
            }
            else {
                break;
            }
        }
        if (debugFlag) { debugWriter.write("<FuncRParams>\n"); }
        return funcRParams;
    }

    public BiOperandExp parseConstExp() throws Exception {
        BiOperandExp biOperandExp = parseAddExp();
        if (debugFlag) { debugWriter.write("<ConstExp>\n"); }
        return biOperandExp;
    }

    // 检测当前的符号是否是给定的类型，如果是，就让token指针指向下一个，否则报错
    public void skipSign(Token.TokenType tokenType, Character errorCode) throws Exception {
        if (lexer.getCurrentToken().getType() != tokenType) {
            if (errorCode != null) { // 如果 errorCode 不是 null, 那么就把当前的错误添加到错误列表中
                ProgramException.newException(lexer.tokenPreRead(-1).getLine() + 1, errorCode);
            }
            else {
                error("在 " + lexer.getCurrentToken().getLine() + " 行未检测到" + tokenType + "符号");
            }
        }
        else {
            lexer.nextToken();
        }
    }

    // 抛出错误
    public void error(String msg) throws Exception {
        throw new Exception(msg);
    }

}
