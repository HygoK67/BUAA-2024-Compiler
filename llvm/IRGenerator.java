package llvm;

import lexical.Token;
import symbol.FunctionType;
import symbol.Symbol;
import symbol.SymbolTable;
import symbol.ValueType;
import syntax.nodes.*;
import util.Utilities;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class IRGenerator {

    private FileWriter irWriter;
    private int nextScope;
    private int virtualRegIndex; // 用于进行虚拟寄存器分配
    private int basicBlockIndex; // 用于进行基本块的分配
    private String currentBasicBlockTag;
    private int indentSpaceCount; // 用于控制缩进
    private Value returnValue;    // 用于存储返回值
    private boolean branchedInCurrentBasicBlock; // 标识当前基本块内是否已经产生了跳转操作
    private String forLoopUpdateBBTag; // for 语句更新语句所在基本块标签，用于生成 continue 语句
    private String forLoopEndBBTag; // for 语句结束所在基本块标签，用于生成 break 语句

    private void writeIndent() throws IOException {
        for (int i = 0; i < indentSpaceCount; i++) {
            irWriter.write(" ");
        }
    }

    private String allocBasicBlock() {
        basicBlockIndex++;
        return "bb" + (basicBlockIndex - 1);
    }

    private void resetBasicBlock() {
        basicBlockIndex = 0;
    }

    private void nextLevelIndent() {
        indentSpaceCount+=2;
    }

    private void prevLevelIndent() {
        indentSpaceCount-=2;
    }

    private void resetReg() {
        virtualRegIndex = 0;
    }

    private int allocReg() {
        virtualRegIndex+=1;
        return virtualRegIndex - 1;
    }

    public IRGenerator(FileWriter fileWriter) {
        this.irWriter = fileWriter;
    }

    private void pushScope() { // 进入到子作用域当中，作用域序号+1
        SymbolTable.jumpToSymbolTableByScopeNum(nextScope);
        nextScope += 1;
    }

    private void popScope() { // 返回到父作用域当中
        SymbolTable.backToUpperScope();
    }

    private int currentScope() { // 查询当前的作用域编号
        return SymbolTable.getCurrentSymbolTable().getScopeNum();
    }

    private Symbol getSymbol(String name) {
        return SymbolTable.getCurrentSymbolTable().searchSymbol(name);
    }

    public void codeGen(CompUnit compUnit) throws IOException {
        printCode("declare i32 @getint()          ; 读取一个整数\n" +
                "declare i32 @getchar()     ; 读取一个字符\n" +
                "declare void @putint(i32)      ; 输出一个整数\n" +
                "declare void @putch(i32)       ; 输出一个字符\n" +
                "declare void @putstr(i8*)      ; 输出字符串\n", true);
        nextScope = 1;
        pushScope();
        for (Decl decl : compUnit.decls) {
            codeGen(decl);
        }
        for (FuncDef funcDef : compUnit.funcDefs) {
            codeGen(funcDef);
        }
        codeGen(compUnit.mainFuncDef);
    }

    private void codeGen(Decl decl) throws IOException {
        if (currentScope() == 1) { // 生成全局变量/常量的 IR
            for (VarConstDef varConstDef : decl.varConstDefs) {
                Symbol symbol = getSymbol(varConstDef.ident.name);
                symbol.definedInLLVMIR = true;
                ValueType type = (ValueType) symbol.symbolType;
                printCode("@" + symbol.symbolName + " = ", true);
                symbol.llvmIRSymbol = "@" + symbol.symbolName; // 更新符号表里面的 ir 表示
                if (type.isConst) { // 如果是常量就加上 constant 标签
                    printCode("constant ", false);
                }
                else {
                    printCode("global ", false);
                }
                if (type.arrayLength != null) { // 数组类型
                    String typeTag;
                    if (type.basicType == ValueType.BasicType.CHR) { // 加上类型标签
                        typeTag = "i8";
                    }
                    else {
                        typeTag = "i32";
                    }
                    printCode("[" + type.arrayLength + " x " + typeTag + "] ", false);
                    // 数组初始值
                    printCode("[", false);
                    for (int i = 0; i < type.arrayLength; i++) {
                        printCode(typeTag + " " + symbol.constValues.get(i), false);
                        if (i < type.arrayLength - 1) {
                            printCode(", ", false);
                        }
                    }
                    printCode("]", false);
                }
                else { // 非数组类型
                    if (type.basicType == ValueType.BasicType.CHR) { // 加上类型标签
                        printCode("i8 ", false);
                    }
                    else {
                        printCode("i32 ", false);
                    }
                    // 初始值
                    printCode(String.valueOf(symbol.constValues.get(0)), false);
                }
                printCode("\n", false);
            }
        }
        else { // 生成局部变量的 IR
            for (VarConstDef varConstDef : decl.varConstDefs) {
                Symbol symbol = getSymbol(varConstDef.ident.name);
                symbol.definedInLLVMIR = true;
                ValueType symbolType = (ValueType) symbol.symbolType;
                // 首先在栈上 alloca 一个对应的变量
                int regNum = allocReg();
                printCode("%" + regNum + " = alloca ", true);
                printVariableType(symbolType); // 打印要分配的变量的类型
                printCode("\n", false);
                // 把分配的信息记录到符号表里面去
                symbol.llvmIRSymbol = "%" + regNum;
                // 然后进行变量初始化的操作
                if (symbolType.arrayLength == null) { // 普通变量非数组
                    if (symbol.constValues.get(0) != null) { // 如果变量拥有一个编译期就可以确定的值作为初始值，直接 store
                        printCode("store ", true);
                        printVariableType(symbolType);
                        printCode(" " + symbol.constValues.get(0) + ", ", false); // 打印要存的值
                    }
                    else {
                        Value expValue = codeGen(varConstDef.initVal.expArray.get(0)); // 先生成表达式的代码
                        if (symbolType.basicType == ValueType.BasicType.CHR) {
                            // 如果当前变量是 char 类型，则需要把 i32 的表达式转化为 i8
                            expValue = convertToI8(expValue);
                        }
                        printCode("store ", true);
                        printVariableType(symbolType);
                        printCode(" " + expValue + ", ", false); // 打印要存的值
                    }
                    printVariableType(symbolType);
                    printCode("* " + symbol.llvmIRSymbol, false);
                    printCode("\n", false);
                }
                else { // 数组，给每个元素单独赋值
                    for (int i = 0; i < symbolType.arrayLength; i++) {
                        Value indexValue = getElementPtr(symbol, new Value("" + i, Type.i32()));
                        // store (i32|i8) {initVal[i]}, (i32|i8)* %{regNumPerElement}
                        if (symbol.constValues.get(i) != null) {
                            printCode("store ", true);
                            printBasicType(symbolType);
                            printCode(" " + symbol.constValues.get(i) + ", ", false);
                        }
                        else {
                            Value value = codeGen(varConstDef.initVal.expArray.get(i));
                            // 可能需要对表达式的值进行 trunc
                            if (symbolType.basicType == ValueType.BasicType.CHR) {
                                value = convertToI8(value);
                            }
                            printCode("store ", true);
                            printBasicType(symbolType);
                            printCode(" " + value + ", ", false);
                        }
                        printBasicType(symbolType);
                        printCode("* " + indexValue + "\n", false);
                    }
                }
            }
        }
    }

    private void codeGen(FuncDef funcDef) throws IOException {
        printCode("define ", true);
        if (funcDef.isMain) {
            printCode("i32 @main() {\n", false);
            resetBasicBlock();
            String firstBBTag = allocBasicBlock();
            printBasicBlock(firstBBTag);

            nextLevelIndent();
            resetReg();
            pushScope();
            int returnValPtr = allocReg(); // 分配返回值
            printCode("%" + returnValPtr + " = alloca i32\n", true);
            printCode("store i32 0, i32* %" + returnValPtr + "\n", true);
            this.returnValue = new Value("%" + returnValPtr, Type.i32ptr());
        }
        else {
            Symbol funcSymbol = getSymbol(funcDef.ident.name);
            funcSymbol.definedInLLVMIR = true;
            FunctionType funcType = (FunctionType) funcSymbol.symbolType;
            if (funcType.returnType == FunctionType.ReturnType.VOID) { // 答应函数类型
                printCode("void ", false);
            }
            else if (funcType.returnType == FunctionType.ReturnType.INT) {
                printCode("i32 ", false);
            }
            else {
                printCode("i8 ", false);
            }
            printCode("@" + funcSymbol.symbolName, false); // 打印函数名字
            funcSymbol.llvmIRSymbol = "@" + funcSymbol.symbolName;
            // 打印参数列表
            resetReg(); // 重制虚拟寄存器编号
            pushScope(); // 查找参数，符号表需要进入到函数体内的作用域
            printCode("(", false);
            for (int i = 0;i < funcType.paramNames.size();i++) {
                // 分配虚拟寄存器
                int regNum = allocReg();
                // 更新符号表的 ir 项目
                getSymbol(funcType.paramNames.get(i)).llvmIRSymbol = "%" + regNum;
                printParamType(funcType.paramTypes.get(i));
                printCode(" %" + regNum ,false);
                if (i < funcType.paramTypes.size() - 1) {
                    printCode(", ", false);
                }
            }
            printCode(") {\n", false);
            resetBasicBlock();
            String firstBBTag = allocBasicBlock();
            printBasicBlock(firstBBTag);

            nextLevelIndent();
            if (funcType.returnType == FunctionType.ReturnType.INT) {
                int returnValPtr = allocReg(); // 分配返回值
                printCode("%" + returnValPtr + " = alloca i32\n", true);
                printCode("store i32 0, i32* %" + returnValPtr + "\n", true);
                this.returnValue = new Value("%" + returnValPtr, Type.i32ptr());
            }
            else if (funcType.returnType == FunctionType.ReturnType.CHR) {
                int returnValPtr = allocReg(); // 分配返回值
                printCode("%" + returnValPtr + " = alloca i8\n", true);
                printCode("store i8 0, i8* %" + returnValPtr + "\n", true);
                this.returnValue = new Value("%" + returnValPtr, Type.i8ptr());
            }
            else {
                this.returnValue = null;
            }
            // 对于非主函数，要把所有的参数先都保存到栈上
            for (int i = 0;i < funcType.paramNames.size();i++) {
                int regNum = allocReg();
                printCode("%" + regNum + " = alloca ", true);
                printParamType(funcType.paramTypes.get(i));
                printCode("\n", false);
                // 生成 store 语句 store (||) {}
                printCode("store " ,true);
                printParamType(funcType.paramTypes.get(i)); // 打印参数类型
                printCode(" " + getSymbol(funcType.paramNames.get(i)).llvmIRSymbol + ", ",false); // 打印函数签名中为参数分配的虚拟寄存器编号
                // 打印 type* {regNum}
                printParamType(funcType.paramTypes.get(i));
                printCode("* %" + regNum ,false);
                printCode("\n", false);
                // 最后更新符号表内当前符号的 llvm ir 表示
                getSymbol(funcType.paramNames.get(i)).llvmIRSymbol = "%" + regNum;
                getSymbol(funcType.paramNames.get(i)).definedInLLVMIR = true;
            }
        }
        // 接下来正式生成函数体
        codeGen(funcDef.block);
        // 生成返回语句
        if (this.returnValue == null && !this.branchedInCurrentBasicBlock) {
            // 如果当前函数是 void 类型的函数并且还没有进行返回（AST里面省略了）
            // 就添加上一个 ret void
            printCode("br label %bbreturn\n", true);
        }
        printBasicBlock("bbreturn");
        if (this.returnValue == null) { // void 返回类型的函数直接返回
            printCode("ret void\n", true);
        }
        else {
            int retValRegNum = allocReg();
            if (this.returnValue.type.basicType == Type.BasicType.i32ptr) {
                printCode("%" + retValRegNum + " = load i32, i32* " + this.returnValue + "\n", true);
                printCode("ret i32 %" + retValRegNum + "\n", true);
            }
            else {
                printCode("%" + retValRegNum + " = load i8, i8* " + this.returnValue + "\n", true);
                printCode("ret i8 %" + retValRegNum + "\n", true);
            }
        }
        popScope(); // 最后需要回到上一层作用域并且重置缩进
        prevLevelIndent();
        printCode("}\n", true);
    }

    private void codeGen(Block block) throws IOException {
        for (BlockItem blockItem : block.blockItems) {
            if (blockItem.decl != null) {
                codeGen(blockItem.decl);
            }
            else {
                codeGen(blockItem.stmt);
            }
        }
    }

    private void codeGen(Stmt stmt) throws IOException {
        if (this.branchedInCurrentBasicBlock) { // 如果当前基本块内已经产生了跳转，那么需要新建一个基本块来保存后续的指令
            printBasicBlock(allocBasicBlock());
        }
        if (stmt.caseNum == 0) { // 'lval' = 'exp'
            Symbol symbol = getSymbol(stmt.lval0.ident.name);
            ValueType symbolType = (ValueType) symbol.symbolType;
            if (stmt.lval0.exp != null) { // 数组元素赋值
                Value indexValue = codeGen(stmt.lval0.exp);
                Value addrValue = getElementPtr(symbol, indexValue); // 获得数组元素的地址
                Value valueToStore = codeGen(stmt.exp0); // 获取要存储的值
                if (symbolType.basicType == ValueType.BasicType.CHR) { // 转化为 i8
                    valueToStore = convertToI8(valueToStore);
                }
                // 生成 store 指令
                printCode("store " + valueToStore.type + " " + valueToStore.value, true);
                printCode(", " + addrValue.type + " " + addrValue.value + "\n", false);
            }
            else { // 普通变量赋值
                Value addrValue;
                if (symbolType.basicType == ValueType.BasicType.CHR) {
                    addrValue = new Value(symbol.llvmIRSymbol, Type.i8ptr());
                }
                else {
                    addrValue = new Value(symbol.llvmIRSymbol, Type.i32ptr());
                }
                Value valueToStore = codeGen(stmt.exp0); // 获取要存储的值
                if (symbolType.basicType == ValueType.BasicType.CHR) { // 转化为 i8
                    valueToStore = convertToI8(valueToStore);
                }
                // 生成 store 指令
                printCode("store " + valueToStore.type + " " + valueToStore.value, true);
                printCode(", " + addrValue.type + " " + addrValue.value + "\n", false);
            }
        }
        else if (stmt.caseNum == 1) { // [exp];
            if (stmt.exp1 != null) {
                codeGen(stmt.exp1);
            }
        }
        else if (stmt.caseNum == 2) { // stmt -> Block
            pushScope();
            codeGen(stmt.block2);
            popScope();
        }
        else if (stmt.caseNum == 3) { //  if 语句
            // 首先获取当前的基本块，并分配三个基本块
            String firstCondBBTag = this.currentBasicBlockTag;
            String condTrueBBTag = allocBasicBlock(); // 条件为真跳转到的基本块
            String condFalseBBTag = allocBasicBlock(); // 条件为假跳转到的基本块
            String endBBTag = allocBasicBlock(); // 结束的基本块
            // TODO 生成条件代码
            codeGen(stmt.condExp3, condFalseBBTag, condTrueBBTag);

            printBasicBlock(condTrueBBTag); // 打印基本块标签
            codeGen(stmt.ifStmtIf3); // 生成条件为真执行的语句
            if (!this.branchedInCurrentBasicBlock) {
                printCode("br label %" + endBBTag + "\n", true); // 执行结束跳转到结束基本块
            }

            printBasicBlock(condFalseBBTag); // 打印基本块标签
            if (stmt.ifStmtElse3 != null) { // 如果有 else 语句，就继续生成
                codeGen(stmt.ifStmtElse3);
            }
            if (!this.branchedInCurrentBasicBlock) {
                printCode("br label %" + endBBTag + "\n", true); // 执行结束跳转到结束基本块
            }

            printBasicBlock(endBBTag); // 打印出口基本块标签
        }
        else if (stmt.caseNum == 4) { // for 语句
            // 首先获取当前的基本块，并分配四个基本块
            String crtBBTag = this.currentBasicBlockTag; // 当前所在的基本块，其包含有 for 语句的初始 stmt
            String condExpBBTag = allocBasicBlock(); // 条件判断所在的基本块
            String stmtBBTag = allocBasicBlock(); // 循环体所在的开始基本块
            String updateBBTag = allocBasicBlock(); // 循环更新语句所在的基本块
            String endBBTag = allocBasicBlock(); // 循环结束后的第一个基本块
            this.forLoopEndBBTag = endBBTag;
            this.forLoopUpdateBBTag = updateBBTag;

            if (stmt.forStmtA4 != null) { // 如果存在初始化语句，那么对其进行代码生成
                codeGen(stmt.forStmtA4);
            }
            printCode("br label %" + condExpBBTag + "\n", true);

            printBasicBlock(condExpBBTag); // 进入新的条件判断基本块
            if (stmt.condExp4 != null) {
                codeGen(stmt.condExp4, endBBTag, stmtBBTag); // 条件满足就到循环体，不满足则直接跳转到最后
            } else {
                printCode("br label %" + stmtBBTag + "\n", true); // 如果没有条件，则直接跳转到循环体
            }

            printBasicBlock(stmtBBTag);
            codeGen(stmt.stmt4); // 生成循环体的代码
            if (!this.branchedInCurrentBasicBlock) { // 如果当前所在的基本块内部没有产生跳转操作, 则最后一步跳转到更新语句
                printCode("br label %" + updateBBTag + "\n", true);
            }

            printBasicBlock(updateBBTag);
            if (stmt.forStmtB4 != null) {
                codeGen(stmt.forStmtB4);
            }
            printCode("br label %" + condExpBBTag + "\n", true); // 更新完成后跳转回到条件判断

            // 最后打印出口基本块标签
            printBasicBlock(endBBTag);

        }
        else if (stmt.caseNum == 5) { // break 语句
            printCode("br label %" + this.forLoopEndBBTag + "\n", true);
            this.branchedInCurrentBasicBlock = true;
        }
        else if (stmt.caseNum == 6) { // continue 语句
            printCode("br label %" + this.forLoopUpdateBBTag + "\n", true);
            this.branchedInCurrentBasicBlock = true;
        }
        else if (stmt.caseNum == 7) { // stmt -> return [exp];
            if (stmt.returnExp7 == null) {
                printCode("br label %bbreturn\n", true);
            }
            else {
                Value returnValue = codeGen(stmt.returnExp7);
                if (SymbolTable.getCurrentSymbolTable().getCurrentReturnType() == FunctionType.ReturnType.CHR) {
                    returnValue = convertToI8(returnValue);
                }
                printCode("store " + returnValue.type + " " + returnValue.value + ", " + this.returnValue.type + " " + this.returnValue.value + "\n", true);
                printCode("br label %bbreturn\n", true);
            }
            this.branchedInCurrentBasicBlock = true;
        }
        else if (stmt.caseNum == 8) { // getint()
            Symbol symbol = getSymbol(stmt.lval8.ident.name);
            ValueType symbolType = (ValueType) symbol.symbolType;
            if (stmt.lval8.exp != null) { // 数组元素赋值
                Value indexValue = codeGen(stmt.lval8.exp);
                Value addrValue = getElementPtr(symbol, indexValue); // 获得数组元素的地址
                int regNum = allocReg();
                printCode("%" + regNum + " = call i32 @getint()\n", true);
                Value valueToStore = new Value("%" + regNum, Type.i32());
                if (symbolType.basicType == ValueType.BasicType.CHR) { // 转化为 i8
                    valueToStore = convertToI8(valueToStore);
                }
                // 生成 store 指令
                printCode("store " + valueToStore.type + " " + valueToStore.value, true);
                printCode(", " + addrValue.type + " " + addrValue.value + "\n", false);
            }
            else { // 普通变量赋值
                Value addrValue;
                if (symbolType.basicType == ValueType.BasicType.CHR) {
                    addrValue = new Value(symbol.llvmIRSymbol, Type.i8ptr());
                }
                else {
                    addrValue = new Value(symbol.llvmIRSymbol, Type.i32ptr());
                }
                int regNum = allocReg(); // 分配一个寄存器来存储要保存的值
                printCode("%" + regNum + " = call i32 @getint()\n", true);
                Value valueToStore = new Value("%" + regNum, Type.i32());
                if (symbolType.basicType == ValueType.BasicType.CHR) { // 转化为 i8
                    valueToStore = convertToI8(valueToStore);
                }
                // 生成 store 指令
                printCode("store " + valueToStore.type + " " + valueToStore.value, true);
                printCode(", " + addrValue.type + " " + addrValue.value + "\n", false);
            }
        }
        else if (stmt.caseNum == 9) { // getchar()
            Symbol symbol = getSymbol(stmt.lval9.ident.name);
            ValueType symbolType = (ValueType) symbol.symbolType;
            if (stmt.lval9.exp != null) { // 数组元素赋值
                Value indexValue = codeGen(stmt.lval9.exp);
                Value addrValue = getElementPtr(symbol, indexValue); // 获得数组元素的地址
                int regNum = allocReg();
                printCode("%" + regNum + " = call i32 @getchar()\n", true);
                Value valueToStore = new Value("%" + regNum, Type.i32());
                if (symbolType.basicType == ValueType.BasicType.CHR) { // 转化为 i8
                    valueToStore = convertToI8(valueToStore);
                }
                // 生成 store 指令
                printCode("store " + valueToStore.type + " " + valueToStore.value, true);
                printCode(", " + addrValue.type + " " + addrValue.value + "\n", false);
            }
            else { // 普通变量赋值
                Value addrValue;
                if (symbolType.basicType == ValueType.BasicType.CHR) {
                    addrValue = new Value(symbol.llvmIRSymbol, Type.i8ptr());
                }
                else {
                    addrValue = new Value(symbol.llvmIRSymbol, Type.i32ptr());
                }
                int regNum = allocReg(); // 分配一个寄存器来存储要保存的值
                printCode("%" + regNum + " = call i32 @getchar()\n", true);
                Value valueToStore = new Value("%" + regNum, Type.i32());
                if (symbolType.basicType == ValueType.BasicType.CHR) { // 转化为 i8
                    valueToStore = convertToI8(valueToStore);
                }
                // 生成 store 指令
                printCode("store " + valueToStore.type + " " + valueToStore.value, true);
                printCode(", " + addrValue.type + " " + addrValue.value + "\n", false);
            }
        }
        else if (stmt.caseNum == 10) { // printf()
            String formatString = stmt.stringConst10.getToken();
            formatString = formatString.substring(1, formatString.length() - 1);
            int expIndex = 0;
            for (int i = 0;i < formatString.length();i++) { // 遍历字符串
                if (i < formatString.length() - 1) {
                    if (formatString.charAt(i) == '%' && formatString.charAt(i + 1) == 'c') { // 输出数字
                        Value valueToOutput = codeGen(stmt.exps10.get(expIndex));
                        outputAChar(valueToOutput);
                        expIndex++;i++;continue;
                    }
                    else if (formatString.charAt(i) == '%' && formatString.charAt(i + 1) == 'd') { //输出字符
                        Value valueToOutput = codeGen(stmt.exps10.get(expIndex));
                        outputANumber(valueToOutput);
                        expIndex++;i++;continue;
                    }
                    else if (formatString.charAt(i) == '\\' && formatString.charAt(i + 1) == 'n') { // 输出换行
                        outputAChar(new Value("10", Type.i32()));
                        i++;continue;
                    }
                }
                // 原样输出
                Value valueToOutput = new Value(
                        String.valueOf((int) formatString.charAt(i)),
                        Type.i32()
                );
                outputAChar(valueToOutput);
            }
        }
        else {
            // TODO
            throw new IOException("暂不支持!");
        }
    }

    private void codeGen(BiOperandExp exp, String nextCondTag, String destTag) throws IOException {
        // 这个函数专门用于生成条件语句中短路求值的代码，exp 是目前需要解析的条件表达式，
        // nextCondTag 是当前的条件不满足时，需要跳转到的下一个基本块标签
        // destTag 是条件满足时需要跳转到的下一个标签

        if (exp.operator == null) { // 表达式的操作符是空的
            if (exp.leftElement instanceof BiOperandExp) { // 如果左节点是一个二元表达式，则继续进行代码生成
                BiOperandExp subExp = (BiOperandExp) exp.leftElement;
                codeGen(subExp, nextCondTag, destTag);
            }
            else if (exp.leftElement instanceof UnaryExp) { // 如果左节点是一个一元表达式，则可以根据该表达式的值和 0 的关系来进行跳转
                UnaryExp subExp = (UnaryExp) exp.leftElement;
                Value expValue = codeGen(subExp);
                int regNum = allocReg();
                printCode("%" + regNum + " = icmp ne i32 " + expValue + ", 0\n", true);
                printCode("br i1 %" + regNum + ", " + "label %" + destTag + ", label %" + nextCondTag + "\n", true);
            }
            else {
                throw new IOException("在进行条件表达式生成时遇到了不支持的 leftElement!");
            }
        }
        else {
            // 表达式的操作符不是空的, 分为以下几种情况
            if (exp.operator.getType() == Token.TokenType.AND) {
                // 操作符是 && 符号，需要进行短路求值
                // 左侧是 LandExp 右侧是 EqExp
                String bbTag = allocBasicBlock(); // 为当前的条件分配一个基本块，则左子树失败时会跳转到当前基本块进行判断
                codeGen((BiOperandExp) exp.leftElement, nextCondTag, bbTag); // 左侧条件满足，则不发生跳转，如果不满足则立即跳转
                printBasicBlock(bbTag);
                codeGen((BiOperandExp) exp.rightElement, nextCondTag, destTag);
            }
            else if (exp.operator.getType() == Token.TokenType.OR) {
                // 操作符是 || 符号，需要进行短路求值
                // 左侧是 LorExp 右侧是 LandExp
                String bbTag =  allocBasicBlock(); // 为当前的右侧条件分配一个基本块，则左子树失败时会跳转到当前基本块进行判断
                codeGen((BiOperandExp) exp.leftElement, bbTag, destTag); // 左侧的 or 表达式如果有任何一个条件为 true，则整体为 true，跳转到 destTag, 如果所有条件都false，则回到当前所在的 tag
                printBasicBlock(bbTag);
                codeGen((BiOperandExp) exp.rightElement, nextCondTag, destTag); // 右侧的 and 表达式如果有任何一个条件为 false, 则and表达式整体为 false, 进而整个 or 表达式也是 false，直接跳到 destTag, 否则跳转到 nextCondTag 进行检查
            }
            else {
                // 操作符是其他符号，不需要进行短路求值，调用 codeGen(exp) 得到其值然后与 0 进行比较，生成对应的代码即可，可以仿照上面的 unaryExp 那一块
                Value expValue = codeGen(exp);
                int regNum = allocReg();
                printCode("%" + regNum + " = icmp ne i32 " + expValue + ", 0\n", true);
                printCode("br i1 %" + regNum + ", " + "label %" + destTag + ", label %" + nextCondTag + "\n", true);
            }
        }
    }

    private Value codeGen(BiOperandExp exp) throws IOException {
        if (exp.operator == null) {
            // 操作符为null有两种情况，一种是 leftElement 是BiOperandExp，一种是leftElement是UnaryExp
            if (exp.leftElement instanceof UnaryExp) {
                return codeGen((UnaryExp) exp.leftElement);
            }
            else if (exp.leftElement instanceof BiOperandExp) {
                return codeGen((BiOperandExp) exp.leftElement);
            }
            else {
                throw new IOException("不支持的 exp 类型!");
            }
        }
        else { // 操作符不为 null, 一定返回 i32 类型的一个 value
            Value leftValue;
            Value rightValue;
            if (exp.leftElement instanceof UnaryExp) {
                leftValue = codeGen((UnaryExp) exp.leftElement);
            }
            else {
                leftValue = codeGen((BiOperandExp) exp.leftElement);
            }
            if (exp.rightElement instanceof UnaryExp) {
                rightValue = codeGen((UnaryExp) exp.rightElement);
            }
            else {
                rightValue = codeGen((BiOperandExp) exp.rightElement);
            }
            int regNum = allocReg();
            switch (exp.operator.getType()) {
                case PLUS -> printCode("%" + regNum + " = add i32 " + leftValue + ", " + rightValue + "\n", true);
                case MINU -> printCode("%" + regNum + " = sub i32 " + leftValue + ", " + rightValue + "\n", true);
                case MULT -> printCode("%" + regNum + " = mul i32 " + leftValue + ", " + rightValue + "\n", true);
                case DIV -> printCode("%" + regNum + " = sdiv i32 " + leftValue + ", " + rightValue + "\n", true);
                case MOD -> printCode("%" + regNum + " = srem i32 " + leftValue + ", " + rightValue + "\n", true);
                case AND -> {} // 不应该在这里出现，应该在条件语句那里出现
                case OR -> {} // 不应该在这里出现，应该在条件语句那里出现
                case EQL -> {
                    printCode("%" + regNum + " = icmp eq i32 " + leftValue + ", " + rightValue + "\n", true); // 首先进行比较操作
                    return convertFromI1ToI32(new Value("%" + regNum, Type.i1())); // 然后再把 i1 转化为 i32 返回
                }
                case NEQ -> {
                    printCode("%" + regNum + " = icmp ne i32 " + leftValue + ", " + rightValue + "\n", true); // 首先进行比较操作
                    return convertFromI1ToI32(new Value("%" + regNum, Type.i1())); // 然后再把 i1 转化为 i32 返回
                }
                case LSS -> {
                    printCode("%" + regNum + " = icmp slt i32 " + leftValue + ", " + rightValue + "\n", true); // 首先进行比较操作
                    return convertFromI1ToI32(new Value("%" + regNum, Type.i1())); // 然后再把 i1 转化为 i32 返回
                }
                case LEQ -> {
                    printCode("%" + regNum + " = icmp sle i32 " + leftValue + ", " + rightValue + "\n", true); // 首先进行比较操作
                    return convertFromI1ToI32(new Value("%" + regNum, Type.i1())); // 然后再把 i1 转化为 i32 返回
                }
                case GRE -> {
                    printCode("%" + regNum + " = icmp sgt i32 " + leftValue + ", " + rightValue + "\n", true); // 首先进行比较操作
                    return convertFromI1ToI32(new Value("%" + regNum, Type.i1())); // 然后再把 i1 转化为 i32 返回
                }
                case GEQ -> {
                    printCode("%" + regNum + " = icmp sge i32 " + leftValue + ", " + rightValue + "\n", true); // 首先进行比较操作
                    return convertFromI1ToI32(new Value("%" + regNum, Type.i1())); // 然后再把 i1 转化为 i32 返回
                }
            }
            return new Value("%" + regNum, Type.i32());
        }
    }

    private Value codeGen(UnaryExp exp) throws IOException {
        // 生成一元表达式的代码
        if (exp.primaryExp != null) { // 基本表达式
            return codeGen(exp.primaryExp);
        }
        else if (exp.ident != null) { // 函数调用
            return codeGen(exp.ident, exp.funcRParams);
        }
        else if (exp.unaryOp != null) { // 一元表达式
            Value value = codeGen(exp.unaryExp);
            if (exp.unaryOp.getType() == Token.TokenType.PLUS) {
                return value;
            }
            else if (exp.unaryOp.getType() == Token.TokenType.MINU) {
                int regNum = allocReg();
                printCode("%" + regNum + " = sub i32 0, " + value + "\n", true);
                return new Value("%" + regNum, Type.i32());
            }
            else if (exp.unaryOp.getType() == Token.TokenType.NOT) {
                // %x = icmp ne i32 {value}, 0
                // %y = xor i1 %x, i1 1
                // %z = zext i1 %y to i32
                // %z 即为所得
                int regNum = allocReg();
                printCode("%" + regNum + " = icmp ne i32 " + value + ", 0\n", true);
                int regNum2 = allocReg();
                printCode("%" + regNum2 + " = xor i1 %" + regNum + ", 1\n", true);
                return convertFromI1ToI32(new Value("%" + regNum2, Type.i1())); // 最后转化到 i32
            }
            else {
                throw new IOException("不支持的 UnaryExp 类型!");
            }
        }
        else {
            throw new IOException("不支持的 UnaryExp 类型!");
        }
    }

    private Value codeGen(PrimaryExp exp) throws IOException {
        // 生成基本表达式的代码
        if (exp.exp != null) {
            return codeGen(exp.exp);
        }
        else if (exp.lval != null) {
            return codeGen(exp.lval);
        }
        else if (exp.character != null) {
            // ASCII 范围 32-126，不会出现需要符号扩展的情况
            return new Value("" + Utilities.getASCII(exp.character.getToken()), Type.i32());
        }
        else if (exp.number != null) {
            return new Value(exp.number.getToken(), Type.i32());
        }
        else {
            throw new IOException("不支持的 PrimaryExp!");
        }
    }

    private Value codeGen(Lval lval) throws IOException {
        // 有可能是数组 arr[i] 或者普通变量 var 或者数组变量本身 arr (在函数调用中出现)
        Symbol symbol = SymbolTable.getCurrentSymbolTable().searchSymbolInCodeGen(lval.ident.name); // 首先检索符号表，找到对应的符号
        ValueType symbolType = (ValueType) symbol.symbolType;
        if (lval.exp == null) {
            if (symbolType.arrayLength == null) {
                // 普通变量 %{regNum} = load (i32|i8), (i32|i8)* {llvmirsymbol}
                int regNum = allocReg();
                printCode("%" + regNum + " = load ", true);
                printBasicType(symbolType);
                printCode(", ", false);
                printBasicType(symbolType);
                printCode("* ", false);
                printCode(symbol.llvmIRSymbol + "\n", false);
                if (symbolType.basicType == ValueType.BasicType.INT) {
                    return new Value("%" + regNum, Type.i32());
                }
                else {
                    return convertFromI8ToI32(new Value("%" + regNum, Type.i8()));
                }
            }
            else { // 数组变量
                // 需要先获取数组的第一个元素的地址
                Value firstElemPtr = getElementPtr(symbol, new Value("0", Type.i32()));
                return firstElemPtr;
            }
        }
        else {
            // 数组 getelementptr inbounds [n x (i32|i8)], [n x (i32|i8)]* {之前分配的虚拟寄存器}, (i32|i8) 0, (i32|i8) {index}
            Value indexValue = codeGen(lval.exp); // 数组的索引值
            Value addrValue = getElementPtr(symbol, indexValue);
            int regNum = allocReg();
            printCode("%" + regNum + " = load ", true);
            printBasicType(symbolType);
            printCode(", ", false);
            printBasicType(symbolType);
            printCode("* " + addrValue + "\n", false);
            if (symbolType.basicType == ValueType.BasicType.INT) {
                return new Value("%" + regNum, Type.i32());
            }
            else {
                return convertFromI8ToI32(new Value("%" + regNum, Type.i8()));
            }
        }
    }

    private Value codeGen(Ident ident, FuncRParams funcRParams) throws IOException {
        Symbol symbol = getSymbol(ident.name); // 取得函数符号
        FunctionType functionType = (FunctionType) symbol.symbolType;
        ArrayList<Value> realParamValues = new ArrayList<>(); // 函数实参的各个值
        if (funcRParams != null) {
            for (int i = 0;i < funcRParams.exps.size();i++) { // 分别解析每一个实参，并生成对应的代码
                realParamValues.add(codeGen(funcRParams.exps.get(i))); // 根据约定，表达式的返回一定是 i32 类型
                if (functionType.paramTypes.get(i).arrayLength == null // 如果对应的参数不是数组并且需要一个 char, 则进行 i32 到 i8 的转换
                    && functionType.paramTypes.get(i).basicType == ValueType.BasicType.CHR) {
                    realParamValues.set(i, convertToI8(realParamValues.get(i)));
                }
            }
        }
        // %result = call <return_type> @function_name(<arg_type> <arg_val>, ...)
        if (functionType.returnType == FunctionType.ReturnType.VOID) {
            printCode("call void @" + ident.name + "(", true);
            // 打印出所有的参数，类型 + 值
            // 函数的实参类型只有可能有这几种情况: i32, i8, i32*, i8*
            for (int i = 0; i < realParamValues.size();i++) {
                printCode(realParamValues.get(i).type + " " + realParamValues.get(i).value, false);
                if (i < realParamValues.size() - 1) {
                    printCode(", ", false);
                }
            }
            printCode(")\n", false);
            return new Value("", Type.vo());
        }
        else {
            int resultRegNum = allocReg();
            printCode("%" + resultRegNum + " = call " +
                    (functionType.returnType == FunctionType.ReturnType.INT ? "i32" : "i8")
                    + " @" + ident.name + "(", true);
            // 打印出所有的参数，类型 + 值
            for (int i = 0; i < realParamValues.size();i++) {
                printCode(realParamValues.get(i).type + " " + realParamValues.get(i).value, false);
                if (i < realParamValues.size() - 1) {
                    printCode(", ", false);
                }
            }
            printCode(")\n", false);
            if (functionType.returnType == FunctionType.ReturnType.INT) {
                return new Value("%" + resultRegNum, Type.i32());
            }
            else {
                return convertFromI8ToI32(new Value("%" + resultRegNum, Type.i8() ));
            }
        }
    }

    private void printParamType(ValueType paramValueType) throws IOException { // 打印参数类型
        if (paramValueType.arrayLength != null) { // 打印类型参数
            if (paramValueType.basicType == ValueType.BasicType.INT) {
                printCode("i32*", false);
            }
            else {
                printCode("i8*", false);
            }
        }
        else {
            if (paramValueType.basicType == ValueType.BasicType.INT) {
                printCode("i32", false);
            }
            else {
                printCode("i8", false);
            }
        }
    }

    private void printVariableType(ValueType variableType) throws IOException {
        if (variableType.arrayLength != null) { // 数组类型
            if (variableType.basicType == ValueType.BasicType.INT) {
                printCode("[" + variableType.arrayLength + " x i32]", false);
            }
            if (variableType.basicType == ValueType.BasicType.CHR) {
                printCode("[" + variableType.arrayLength + " x i8]", false);
            }
        }
        else {
            if (variableType.basicType == ValueType.BasicType.INT) {
                printCode("i32", false);
            }
            if (variableType.basicType == ValueType.BasicType.CHR) {
                printCode("i8", false);
            }
        }
    }

    private void printBasicType(ValueType symbolType) throws IOException {
        if (symbolType.basicType == ValueType.BasicType.INT) {
            printCode("i32", false);
        }
        else {
            printCode("i8", false);
        }
    }

    private Value convertFromI8ToI32(Value i8Value) throws IOException {
        if (i8Value.type.basicType != Type.BasicType.i8) {
            throw new IOException("i8 转换为 i32 出错!");
        }
        int regNum = allocReg();
        printCode("%" + regNum + " = sext i8 " + i8Value + " to i32\n", true);
        return new Value("%" + regNum, Type.i32());
    }

    private Value convertFromI1ToI32(Value i1Value) throws IOException {
        if (i1Value.type.basicType != Type.BasicType.i1) {
            throw new IOException("i1 转换为 i32 出错!");
        }
        int regNum = allocReg();
        printCode("%" + regNum + " = zext i1 " + i1Value + " to i32\n", true);
        return new Value("%" + regNum, Type.i32());
    }

    private Value convertToI8(Value i32Value) throws IOException {
        if (i32Value.type.basicType != Type.BasicType.i32) {
            throw new IOException("i32 转换为 i8 出错!");
        }
        int regNum = allocReg();
        printCode("%" + regNum + " = trunc i32 " + i32Value + " to i8\n", true);
        return new Value("%" + regNum, Type.i8());
    }

    private Value getElementPtr(Symbol symbol, Value indexValue) throws IOException {
        // 需要确保 symbol 是一个数组类型，并且其索引有效
        ValueType symbolType = (ValueType) symbol.symbolType;
        int addrRegNum;
        // 如果符号表中存储的类型是 (i32|i8)**
        if (symbolType.arrayLength == 0) {
            // 先 load 从 (i32|i8)** 得到 (i32|i8)*
            int firstElemAddrRegNum = allocReg();
            printCode("%" + firstElemAddrRegNum + " = load ", true);
            printBasicType(symbolType); // i32* 或 i8*
            printCode("*, ", false);
            printBasicType(symbolType); // i32** 或 i8**
            printCode("** " + symbol.llvmIRSymbol + "\n", false);
            // 然后再根据 (i32|i8)* 得到对应元素的地址
            addrRegNum = allocReg();
            printCode("%" + addrRegNum + " = getelementptr inbounds ", true);
            printBasicType(symbolType);
            printCode(", ", false);
            printBasicType(symbolType);
            printCode("*", false);
            printCode(" %" + firstElemAddrRegNum + ", ", false);
            printCode("i32 " + indexValue + "\n", false);
        }
        else { // 如果符号表中存储的类型是 [n x (i32|i8)]*
            addrRegNum = allocReg();
            printCode("%" + addrRegNum + " = getelementptr inbounds ", true);
            printVariableType(symbolType);
            printCode(", ", false);
            printVariableType(symbolType);
            printCode("*", false);
            printCode(" " + symbol.llvmIRSymbol + ", ", false);
            printCode("i32 0, i32 " + indexValue + "\n", false);
        }
        return new Value(
                "%" + addrRegNum,
                symbolType.basicType == ValueType.BasicType.INT ? Type.i32ptr() : Type.i8ptr()
        );
    }

    private void outputAChar(Value outputValue) throws IOException {
        if (outputValue.type.basicType != Type.BasicType.i32) {
            throw new IOException("输出字符时类型错误!");
        }
        printCode("call void @putch(" + outputValue.type + " " + outputValue.value + ")\n", true);
    }

    private void outputANumber(Value outputValue) throws IOException {
        if (outputValue.type.basicType != Type.BasicType.i32) {
            throw new IOException("输出数字时类型错误!");
        }
        printCode("call void @putint(" + outputValue.type + " " + outputValue.value + ")\n", true);
    }

    private void printBasicBlock(String basicBlockTag) throws IOException {
        printCode(basicBlockTag + ":\n", false);
        this.currentBasicBlockTag = basicBlockTag;
        this.branchedInCurrentBasicBlock = false;
    }

    private void printCode(String code, boolean indent) throws IOException {
        if (indent) {
            writeIndent();
        }
        irWriter.write(code);
    }

}
