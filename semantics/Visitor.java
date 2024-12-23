package semantics;

import lexical.Token;
import program.ProgramException;
import symbol.FunctionType;
import symbol.Symbol;
import symbol.SymbolTable;
import symbol.ValueType;
import syntax.nodes.*;
import util.Utilities;

import java.io.FileWriter;

public class Visitor {

    private final boolean debugFlag;
    private final FileWriter debugWriter;

    public Visitor(boolean debugFlag, FileWriter debugWriter) {
        this.debugFlag = debugFlag;
        this.debugWriter = debugWriter;
    }

    public void visitCompUnit(CompUnit compUnit) throws Exception {
        for (Decl decl : compUnit.decls) {
            visitDecl(decl);
        }
        for (FuncDef funcDef : compUnit.funcDefs) {
            visitFuncDef(funcDef);
        }
        visitMainFuncDef(compUnit.mainFuncDef);
    }

    private void visitDecl(Decl decl) throws Exception {
        for (VarConstDef varConstDef : decl.varConstDefs) {
            Symbol symbol = new Symbol();
            symbol.symbolType = new ValueType();
            symbol.scopeNum = SymbolTable.getCurrentSymbolTable().getScopeNum();
            symbol.symbolName = varConstDef.ident.name;
            symbol.defLineNum = varConstDef.lineNum;
            ValueType symbolType = (ValueType) symbol.symbolType;
            symbolType.isConst = varConstDef.isConst;
            symbolType.basicType = (decl.btype == Btype.INT) ? ValueType.BasicType.INT : ValueType.BasicType.CHR;
            if (varConstDef.dimensionConstExp != null) {
                ExpInfo dimensionConstExpInfo = visitExp(varConstDef.dimensionConstExp);
                if (dimensionConstExpInfo != null) {
                    symbolType.arrayLength = dimensionConstExpInfo.value;
                }
            }
            if (symbolType.isConst) { // 当当前声明为常数时，才进行符号表的初始值初始化操作
                initConst(varConstDef.initVal, symbol);
            }
            else { // 普通变量的初始值也需要进行符号表的初始化
                if (varConstDef.initVal != null) { // 程序给出初始值，则使用程序的初始值
                    initConst(varConstDef.initVal, symbol);
                }
                else { // 如果程序没有给出初始值，那么就初始化为0
                    if (symbolType.arrayLength != null) { // 数组初始化，全是0
                        for (int i = 0; i < symbolType.arrayLength; i++) {
                            symbol.constValues.add(0);
                        }
                    }
                    else { // 普通变量初始化，一个0
                        symbol.constValues.add(0);
                    }
                }
            }
            Symbol newSymbol = SymbolTable.getCurrentSymbolTable().insertSymbol(symbol);
            if (newSymbol != null) {
                if (debugFlag) {debugWriter.write(newSymbol + "\n");}
            }
            else {
                ProgramException.newException(varConstDef.lineNum + 1, 'b');
            }
        }
    }

    private void initConst(InitVal initVal, Symbol symbol) throws Exception { // 把常数（或者数组）定义中的常数值赋值到符号表的 Symbol 项目中
        int arrayLength = ((ValueType) symbol.symbolType).arrayLength == null ? 1 : ((ValueType) symbol.symbolType).arrayLength;
        if (initVal.stringConst != null) {
            for (int i = 1; i < initVal.stringConst.length()-1; i++) {
                symbol.constValues.add((int) initVal.stringConst.charAt(i));
            }
            for (int i = initVal.stringConst.length()-2; i < arrayLength; i++) {
                symbol.constValues.add(0);
            }
        }
        else if (initVal.expArray != null) {
            for (int i = 0; i < initVal.expArray.size(); i++) { // 先赋值已经提供的值
                ExpInfo constExpInfo = visitExp(initVal.expArray.get(i));
                if (constExpInfo == null) { // 在常数赋值过程中发现错误，直接返回
                    return;
                }
                if (((ValueType) symbol.symbolType).basicType == ValueType.BasicType.CHR) {
                    if (constExpInfo.value == null) { // 有可能初始值不是常数，是表达式，此时 value 是 null
                        symbol.constValues.add(null);
                    }
                    else {
                        boolean highest = (constExpInfo.value & 0xff) >> 7 == 1;
                        symbol.constValues.add((constExpInfo.value & 0xff) | (highest ? 0xFFFFFF00 : 0));
                    }
                }
                else {
                    symbol.constValues.add(constExpInfo.value);
                }
            }
            for (int i = initVal.expArray.size(); i < arrayLength; i++) {
                symbol.constValues.add(0);
            }
        }
        else {
            error("无法初始化常数(数组)的符号表项目");
        }
    }

    private FunctionType.ReturnType currentReturnType = null; // 分析任何函数的函数体之前，首先设置当前函数的返回值类型

    private void visitFuncDef(FuncDef funcDef) throws Exception {
        Symbol functionSymbol = new Symbol();
        FunctionType newFunctionType = new FunctionType();
        functionSymbol.symbolType = newFunctionType;
        if (funcDef.funcType.getType() == Token.TokenType.VOIDTK) { // 首先填入返回值类型信息
            newFunctionType.returnType = FunctionType.ReturnType.VOID;
        }
        else if (funcDef.funcType.getType() == Token.TokenType.INTTK) {
            newFunctionType.returnType = FunctionType.ReturnType.INT;
        }
        else {
            newFunctionType.returnType = FunctionType.ReturnType.CHR;
        }
        functionSymbol.defLineNum = funcDef.lineNum; // 继续填入函数的其他基本信息，例如定义的行数，名称，以及作用域编号
        functionSymbol.symbolName = funcDef.ident.name;
        functionSymbol.scopeNum = SymbolTable.getCurrentSymbolTable().getScopeNum();
        Symbol functionSymbol2 = SymbolTable.getCurrentSymbolTable().insertSymbol(functionSymbol);
        if (functionSymbol2 == null) { // 打印函数的基本信息或者记录函数重复定义的错误
            ProgramException.newException(funcDef.lineNum + 1, 'b');
        }
        else {
            if (debugFlag) {debugWriter.write(functionSymbol + "\n");}
        }
        // 继续分析函数的参数并将这些参数输出和记录
        SymbolTable.newSymbolTable(newFunctionType.returnType); // 新建一级符号表
        if (funcDef.funcFParams != null) { // 当函数存在参数时, 分析函数参数
            visitFuncFParams(funcDef.funcFParams, newFunctionType);
        }
        currentReturnType = newFunctionType.returnType;
        visitBlock(funcDef.block, false); // 分析函数体
        if (currentReturnType != FunctionType.ReturnType.VOID) {
            if (funcDef.block.blockItems.isEmpty()) {
                ProgramException.newException(funcDef.block.lastRBraceLineNum + 1, 'g');
            }
            else if (funcDef.block.blockItems.get(funcDef.block.blockItems.size()-1).stmt == null) { // 有返回值的函数需要检查最后一个语句是否是返回语句
                ProgramException.newException(funcDef.block.lastRBraceLineNum + 1, 'g');
            }
            else if (funcDef.block.blockItems.get(funcDef.block.blockItems.size()-1).stmt.caseNum != 7) {
                ProgramException.newException(funcDef.block.lastRBraceLineNum + 1, 'g');
            }
        }
        // 退出函数之前别忘了将符号表返回上一级作用域
        SymbolTable.backToUpperScope();
    }

    private void visitFuncFParams(FuncFParams funcFParams, FunctionType functionType) throws Exception {
        for (FuncFParam funcFParam : funcFParams.funcFParams) { // 遍历语法数上的每个形参
            ValueType paramValueType = new ValueType(); // 新建一个形参的类型示例
            paramValueType.arrayLength = funcFParam.isArray ? 0 : null;
            paramValueType.basicType = funcFParam.btype == Btype.INT ? ValueType.BasicType.INT : ValueType.BasicType.CHR;
            paramValueType.isConst = false;
            functionType.paramNames.add(funcFParam.ident.name); // 形参添加到函数类型的参数列表里, 先不管其是否具有重复名字的问题
            functionType.paramTypes.add(paramValueType);
            Symbol newParamSymbol = SymbolTable.getCurrentSymbolTable().insertSymbol(funcFParam.ident.name, paramValueType, funcFParam.lineNum); // 插入符号表
            if (newParamSymbol == null) {
                ProgramException.newException(funcFParam.lineNum + 1, 'b');
            }
            else {
                if (debugFlag) {debugWriter.write(newParamSymbol + "\n");}
            }
        }
    }

    private void visitMainFuncDef(FuncDef mainFuncDef) throws Exception {
        // 主函数不需要对返回值，参数，名字等内容进行分析，直接分析block 即可
        currentReturnType = FunctionType.ReturnType.INT;
        SymbolTable.newSymbolTable(currentReturnType); // 新建一级符号表
        visitBlock(mainFuncDef.block, false);
        SymbolTable.backToUpperScope(); // 返回到上级符号表
        if (mainFuncDef.block.blockItems.isEmpty()) {
            ProgramException.newException(mainFuncDef.block.lastRBraceLineNum + 1, 'g'); // 主函数也需要检查最后一个语句是否是返回语句
        }
        else if (mainFuncDef.block.blockItems.get(mainFuncDef.block.blockItems.size()-1).stmt == null) {
            ProgramException.newException(mainFuncDef.block.lastRBraceLineNum + 1, 'g');
        }
        else if (mainFuncDef.block.blockItems.get(mainFuncDef.block.blockItems.size()-1).stmt.caseNum != 7) {
            ProgramException.newException(mainFuncDef.block.lastRBraceLineNum + 1, 'g');
        }
    }

    private void visitBlock(Block block, boolean inForLoop) throws Exception { // 语法分析{}内部的代码, 其不负责新建或者回退符号表，而应由调用该函数者来操作
        for (BlockItem blockItem : block.blockItems) {
            visitBlockItem(blockItem, inForLoop);
        }
    }

    private void visitBlockItem(BlockItem blockItem, boolean inForLoop) throws Exception {
        if (blockItem.stmt == null) {
            visitDecl(blockItem.decl);
        }
        else {
            visitStmt(blockItem.stmt, inForLoop);
        }
    }

    private void visitStmt(Stmt stmt, boolean inForLoop) throws Exception {
        if (stmt.caseNum == 0) { // 赋值语句, 需要分别 visit 左值 lval0 和 右侧的表达式 exp0
            ExpInfo lvalInfo = visitLval(stmt.lval0);
            if (lvalInfo != null && lvalInfo.type.isConst) {
                ProgramException.newException(stmt.lval0.lineNum + 1, 'h'); // 不能修改常量的值
            }
            visitExp(stmt.exp0);
        }
        else if (stmt.caseNum == 1) { // 单独出现的表达式，直接 visit 该表达式即可
            if (stmt.exp1 != null) { // 情况 1 有可能只是一个 ; 这时 stmt.exp1 也是 null, 直接跳过即可
                visitExp(stmt.exp1);
            }
        }
        else if (stmt.caseNum == 2) { // 出现了新的 block，在这里需要创建新一级的符号表，然后再 visit block
            SymbolTable.newSymbolTable();
            visitBlock(stmt.block2, inForLoop);
            SymbolTable.backToUpperScope();
        }
        else if (stmt.caseNum == 3) { // if 语句
            visitExp(stmt.condExp3);
            visitStmt(stmt.ifStmtIf3, inForLoop);
            if (stmt.ifStmtElse3 != null) {
                visitStmt(stmt.ifStmtElse3, inForLoop);
            }
        }
        else if (stmt.caseNum == 4) { // for 语句
            if (stmt.forStmtA4 != null) {
                visitStmt(stmt.forStmtA4, false);
            }
            if (stmt.condExp4 != null) {
                visitExp(stmt.condExp4);
            }
            if (stmt.forStmtB4 != null) {
                visitStmt(stmt.forStmtB4, false);
            }
            visitStmt(stmt.stmt4, true);
        }
        else if (stmt.caseNum == 5) {
            if (!inForLoop) {
                ProgramException.newException(stmt.lineNum + 1, 'm'); // 在非循环体内部出现了 break 语句
            }
        }
        else if (stmt.caseNum == 6) {
            if (!inForLoop) {
                ProgramException.newException(stmt.lineNum + 1, 'm'); // 在非循环体内部出现了 continue 语句
            }
        }
        else if (stmt.caseNum == 7) {
            if (stmt.returnExp7 != null) {
                if (currentReturnType == FunctionType.ReturnType.VOID) {
                    ProgramException.newException(stmt.lineNum + 1, 'f'); // 无返回值的函数存在异常的返回语句
                }
                else {
                    visitExp(stmt.returnExp7); // 检查返回语句内部的表达式有没有问题
                }
            }
            else { // 返回表达式是空的 即 return;
                if (currentReturnType != FunctionType.ReturnType.VOID) {
                    // error("有返回值的函数在返回语句中什么都没有返回!");
                    // 不需要报出这种错误，因此注释掉了
                }
            }
        }
        else if (stmt.caseNum == 8) {
            ExpInfo lvalInfo = visitLval(stmt.lval8);
            if (lvalInfo != null && lvalInfo.type.isConst) {
                ProgramException.newException(stmt.lval8.lineNum + 1, 'h'); // 不能修改常量的值
            }
        }
        else if (stmt.caseNum == 9) {
            ExpInfo lvalInfo = visitLval(stmt.lval9);
            if (lvalInfo != null && lvalInfo.type.isConst) {
                ProgramException.newException(stmt.lval9.lineNum + 1, 'h'); // 不能修改常量的值
            }
        }
        else if (stmt.caseNum == 10) {
            int placeHolderCount = 0;
            for (int i = 0;i < stmt.stringConst10.getToken().length() - 1;i++) {
                if (stmt.stringConst10.getToken().charAt(i) == '%') {
                    if (stmt.stringConst10.getToken().charAt(i+1) == 'c' || stmt.stringConst10.getToken().charAt(i+1) == 'd') {
                        placeHolderCount++;
                    }
                }
            }
            if (placeHolderCount != stmt.exps10.size()) {
                ProgramException.newException(stmt.lineNum + 1, 'l');
                return;
            }
            for (BiOperandExp exp : stmt.exps10) {
                visitExp(exp);
            }
        }
    }

    private ExpInfo visitLval(Lval lval) throws Exception {
        // 左值要么是数组，要么是变量
        String identName = lval.ident.name;
        Symbol symbol = SymbolTable.getCurrentSymbolTable().searchSymbol(identName);
        if (symbol == null) { // 没有查到对应的符号, 记录错误，并直接返回 null
            ProgramException.newException(lval.lineNum + 1, 'c'); // 左值中出现的标识符未定义
            return null;
        }
        ExpInfo expInfo = new ExpInfo();
        if (symbol.symbolType instanceof FunctionType) {
            error("左值不能是函数!");
            return null;
        }
        ValueType valueType = (ValueType) symbol.symbolType;
        expInfo.type.isConst = valueType.isConst; // 检查当前的ident是否对应常数（或者常数组）
        expInfo.type.basicType = valueType.basicType; // 当前的标识符对应 int 还是 char
        if (valueType.arrayLength != null) { // 说明左值对应的ident是数组
            expInfo.type.arrayLength = valueType.arrayLength;
            if (lval.exp == null) { // 例如 a, 仅代表了对数组变量的引用，不对数组取索引

            }
            else {
                // 当内部的exp不为空时，例如 a[5+2]，代表了对于数组的索引，需要进一步访问子表达式以获得其值
                expInfo.type.arrayLength = null; // 索引表达式不为空，则表达式是普通的值，不再是数组类型
                ExpInfo subExpInfo = visitExp(lval.exp); // 检查表达式
                if (valueType.isConst) { // 如果是常数组，则还需要存储常数值
                    if (subExpInfo == null) {return null;} // 如果访问子表达式发生了异常，那么也就不继续分析当前的左值
                    if (subExpInfo.type.isConst) { // 只有数组本身是常数组并且数组的索引表达式也是常数表达式的时候才能赋值
                        expInfo.value = symbol.constValues.get(subExpInfo.value);
                    }
                }
            }
        }
        else { // 是普通变量
            expInfo.type.arrayLength = null;
            if (valueType.isConst) { // 如果是常量，则还需要存储常数值
                expInfo.value = symbol.constValues.get(0);
            }
        }
        return expInfo;
    }

    private ExpInfo visitExp(BiOperandExp exp) throws Exception {
        if (exp.operator == null) { // 操作符为null有两种情况，一种是 leftElement 是BiOperandExp，一种是leftElement是UnaryExp
            // 操作符为空的情况下，该表达式的返回信息和子表达式完全相同
            if (exp.leftElement instanceof BiOperandExp) { // 左子树是二元表达式
                return visitExp((BiOperandExp) exp.leftElement);
            }
            else if (exp.leftElement instanceof UnaryExp) { // 左子树是一元表达式
                return visitUnaryExp((UnaryExp) exp.leftElement);
            }
            else {
                error("不支持的 exp 类型!");
                return null;
            }
        }
        // 操作符不为 null 的情况下，根据文法，首先返回的值类型一定是 int（非数组）
        // 然后如果左右两侧都是常量，则该表达式也是常量
        // 由于不存在数组变量直接参与运算的情况，因此如果两侧都是常量，则一定能够计算出最终的返回值
        ExpInfo expInfo = new ExpInfo();
        expInfo.type.basicType = ValueType.BasicType.INT;
        expInfo.type.arrayLength = null;
        // 检查左右两侧的子元素是什么类型，根据类型进行visit
        ExpInfo leftElementExpInfo;
        ExpInfo rightElementExpInfo;
        if (exp.leftElement instanceof UnaryExp) {
            leftElementExpInfo = visitUnaryExp((UnaryExp) exp.leftElement);
        }
        else {
            leftElementExpInfo = visitExp((BiOperandExp) exp.leftElement);
        }
        if (exp.rightElement instanceof UnaryExp) {
            rightElementExpInfo = visitUnaryExp((UnaryExp) exp.rightElement);
        }
        else {
            rightElementExpInfo = visitExp((BiOperandExp) exp.rightElement);
        }
        if (leftElementExpInfo == null || rightElementExpInfo == null) { // 如果左右两侧有任何一个子树分析失败，则直接返回 null
            return null;
        }
        expInfo.type.isConst = leftElementExpInfo.type.isConst & rightElementExpInfo.type.isConst;
        if (expInfo.type.isConst) { // 如果发现当前的表达式是常数表达式，则需要计算出对应的值
            switch (exp.operator.getType()) {
                case PLUS -> expInfo.value = leftElementExpInfo.value + rightElementExpInfo.value;
                case MINU -> expInfo.value = leftElementExpInfo.value - rightElementExpInfo.value;
                case MULT -> expInfo.value = leftElementExpInfo.value * rightElementExpInfo.value;
                case DIV -> expInfo.value = leftElementExpInfo.value / rightElementExpInfo.value;
                case MOD -> expInfo.value = leftElementExpInfo.value % rightElementExpInfo.value;
                case AND -> expInfo.value = ((leftElementExpInfo.value != 0) && (rightElementExpInfo.value != 0)) ? 1 : 0;
                case OR -> expInfo.value = ((leftElementExpInfo.value != 0) || (rightElementExpInfo.value != 0)) ? 1 : 0;
                case EQL -> expInfo.value = (leftElementExpInfo.value.equals(rightElementExpInfo.value)) ? 1 : 0;
                case NEQ -> expInfo.value = (leftElementExpInfo.value.equals(rightElementExpInfo.value)) ? 0 : 1;
                case LSS -> expInfo.value = (leftElementExpInfo.value < rightElementExpInfo.value) ? 1 : 0;
                case LEQ -> expInfo.value = (leftElementExpInfo.value <= rightElementExpInfo.value) ? 1 : 0;
                case GRE -> expInfo.value = (leftElementExpInfo.value > rightElementExpInfo.value) ? 1 : 0;
                case GEQ -> expInfo.value = (leftElementExpInfo.value >= rightElementExpInfo.value) ? 1 : 0;
            }
        }
        return expInfo;
    }

    private ExpInfo visitUnaryExp(UnaryExp unaryExp) throws Exception {
        if (unaryExp.primaryExp != null) {
            return visitPrimaryExp(unaryExp.primaryExp);
        }
        else if (unaryExp.ident != null) { // 函数调用的返回值不可能在编译期间算出，但是类型可以填入
            return visitFuncCall(unaryExp.ident, unaryExp.funcRParams);
        }
        else if (unaryExp.unaryExp != null) {
            ExpInfo expInfo = visitUnaryExp(unaryExp.unaryExp);
            if (expInfo == null) { // 如果子表达式返回了空值，那么当前表达式也返回空值
                return null;
            }
            if (unaryExp.unaryOp.getType() == Token.TokenType.PLUS) {
                return expInfo;
            }
            else if (unaryExp.unaryOp.getType() == Token.TokenType.MINU) {
                if (expInfo.type.isConst) {
                    expInfo.value = -expInfo.value;
                }
                return expInfo;
            }
            else if (unaryExp.unaryOp.getType() == Token.TokenType.NOT) {
                if (expInfo.type.isConst) {
                    if (expInfo.value == 0) {
                        expInfo.value = 1;
                    }
                    else {
                        expInfo.value = 0;
                    }
                }
                return expInfo;
            }
            else {
                error("不支持的一元表达式操作符!");
            }
        }
        else {
            error("不支持的 unaryExp 一元表达式!");
            return null;
        }
        return null;
    }

    private ExpInfo visitPrimaryExp(PrimaryExp primaryExp) throws Exception {
        ExpInfo expInfo = new ExpInfo();
        if (primaryExp.exp != null) { // (exp) 类型
            return visitExp(primaryExp.exp);
        }
        else if (primaryExp.lval != null) { // Lval 左值类型
            return visitLval(primaryExp.lval);
        }
        else if (primaryExp.number != null) {
            expInfo.value = Integer.parseInt(primaryExp.number.getToken());
            expInfo.type.isConst = true;
            expInfo.type.arrayLength = null;
            expInfo.type.basicType = ValueType.BasicType.INT;
            return expInfo;
        }
        else if (primaryExp.character != null) {
            expInfo.value = Utilities.getASCII(primaryExp.character.getToken());
            expInfo.type.isConst = true;
            expInfo.type.arrayLength = null;
            expInfo.type.basicType = ValueType.BasicType.CHR;
            return expInfo;
        }
        else {
            error("不支持的 primaryExp!");
        }
        return null;
    }

    private ExpInfo visitFuncCall(Ident funcIdentifier, FuncRParams funcRParams) throws Exception {
        // 首先检查调用的函数是否有定义
        Symbol funcSymbol = SymbolTable.getCurrentSymbolTable().searchSymbol(funcIdentifier.name);
        if (funcSymbol == null) {
            ProgramException.newException(funcIdentifier.lineNum + 1, 'c');
            return null;
        }
        // 如果有定义，则新建 ExpInfo 示例存储了函数返回值的类型
        // 并且新建 functionType 记录符号表中记录的该函数的相关信息
        ExpInfo expInfo = new ExpInfo();
        FunctionType functionType = (FunctionType) funcSymbol.symbolType;
        if (functionType.returnType == FunctionType.ReturnType.VOID) {
            expInfo = null;
        }
        else if (functionType.returnType == FunctionType.ReturnType.INT) {
            expInfo.value = null;
            expInfo.type.isConst = false;
            expInfo.type.arrayLength = null;
            expInfo.type.basicType = ValueType.BasicType.INT;
        }
        else {
            expInfo.value = null;
            expInfo.type.isConst = false;
            expInfo.type.arrayLength = null;
            expInfo.type.basicType = ValueType.BasicType.CHR;
        }
        int len1 = funcRParams == null ? 0 : funcRParams.exps.size();
        int len2 = functionType.paramNames.size();
        if (len1 != len2) {
            ProgramException.newException(funcIdentifier.lineNum + 1, 'd'); // 函数调用和定义参数数量不匹配
            return expInfo;
        }
        for (int i = 0; i < len1; i++) { // 然后遍历每个参数，检查函数调用中每个参数和原始的函数定义的对应参数是否匹配
            // 测试用例保证了不会出现数组名参与运算的情况，
            BiOperandExp funcRExp = funcRParams.exps.get(i);
            ExpInfo funcRExpInfo = visitExp(funcRExp);
            if (funcRExpInfo == null) { // 如果实参表达式出现了问题，则有可能返回 null
                return null;
            }
            ValueType funcRParamType = funcRExpInfo.type; // 分析实参表达式，得到其类型信息
            ValueType funcFParamType = functionType.paramTypes.get(i); // 查找符号表，得到预先定义的参数类型信息
            if (funcRParamType.arrayLength == null && funcFParamType.arrayLength != null) {
                ProgramException.newException(funcIdentifier.lineNum + 1, 'e'); // 向数组类型传递非数组变量
                break;
            }
            if (funcRParamType.arrayLength != null && funcFParamType.arrayLength == null) {
                ProgramException.newException(funcIdentifier.lineNum + 1, 'e'); // 向非数组类型传递数组变量
                break;
            }
            if (funcRParamType.arrayLength != null && funcFParamType.arrayLength != null && funcFParamType.basicType != funcRParamType.basicType) {
                ProgramException.newException(funcIdentifier.lineNum + 1, 'e'); // 形参和实参都是数组，但是类型不匹配
                break;
            }
        }
        return expInfo;
    }

    private void error(String msg) throws Exception {
        throw new Exception(msg);
    }

}
