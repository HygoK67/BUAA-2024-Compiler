package symbol;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class SymbolTable {

    private HashMap<String, Symbol> symbolTable; // 根据名字来查询符号的hashmap
    private ArrayList<Symbol> symbols; // 用于存储各个 symbol 的存储顺序
    private int scopeNum;
    private SymbolTable parentScopeSymbolTable; // 父作用域的符号表指针
    private HashMap<Integer, SymbolTable> sonScopeSymbolTables; // 各个子作用域的符号表指针
    private FunctionType.ReturnType currentReturnType;


    public SymbolTable(int scopeNum, SymbolTable parentScopeSymbolTable, FunctionType.ReturnType currentReturnType) {
        this.symbolTable = new HashMap<>();
        this.symbols = new ArrayList<>();
        this.scopeNum = scopeNum;
        this.parentScopeSymbolTable = parentScopeSymbolTable;
        this.sonScopeSymbolTables = new HashMap<>();
        this.currentReturnType = currentReturnType;
    }

    // 对于某一个特定的符号表所要用到的函数
    // 插入一个符号, 插入成功返回 true, 如果发现已经插入了则返回 false
    public Symbol insertSymbol(String symbolName, Type symbolType, int defLineNum) {
        if (symbolTable.containsKey(symbolName)) {
            return null;
        }
        else {
            Symbol newSymbol = new Symbol();
            newSymbol.symbolName = symbolName;
            newSymbol.symbolType = symbolType;
            newSymbol.scopeNum = scopeNum;
            newSymbol.defLineNum = defLineNum;
            symbolTable.put(symbolName, newSymbol);
            symbols.add(newSymbol);
            return newSymbol;
        }
    }

    public Symbol insertSymbol(Symbol symbol) {
        if (symbolTable.containsKey(symbol.symbolName)) {
            return null;
        }
        else {
            symbolTable.put(symbol.symbolName, symbol);
            symbols.add(symbol);
            return symbol;
        }
    }

    // 在当前的符号表里查询一个符号，
    // 如果在当前的符号表里查询成功，则直接返回该符号的相关信息
    // 如果查询失败并且存在上层符号表的话就去上层符号表里面寻找
    // 如果查询失败并且不存在上层符号表，则说明该符号未定义，直接返回 null
    public Symbol searchSymbol(String symbolName) {
        if (symbolTable.containsKey(symbolName)) {
            return symbolTable.get(symbolName);
        }
        else {
            if (parentScopeSymbolTable != null) {
                return parentScopeSymbolTable.searchSymbol(symbolName);
            }
            else {
                return null;
            }
        }
    }

    public int getScopeNum() {
        return scopeNum;
    }

    public FunctionType.ReturnType getCurrentReturnType() {
        return currentReturnType;
    }

    // 符号表全局需要用到的函数和变量
    private static int symbolTableCount; // 记录了全部的符号表数量，初始时为 1

    private static SymbolTable currentSymbolTable;
    private static ArrayList<SymbolTable> symbolTableList;

    static {
        symbolTableCount = 1;
        currentSymbolTable = new SymbolTable(1, null, null);
        symbolTableList = new ArrayList<>();
        symbolTableList.add(currentSymbolTable);
    }

    public static SymbolTable getCurrentSymbolTable() {
        return currentSymbolTable;
    }

    public static SymbolTable newSymbolTable() { // 同函数内新建作用域
        symbolTableCount += 1;
        SymbolTable newSymbolTable = new SymbolTable(symbolTableCount, currentSymbolTable, currentSymbolTable.currentReturnType);
        currentSymbolTable.sonScopeSymbolTables.put(newSymbolTable.scopeNum, newSymbolTable);
        currentSymbolTable = newSymbolTable;
        symbolTableList.add(currentSymbolTable);
        return currentSymbolTable;
    }

    public static SymbolTable newSymbolTable(FunctionType.ReturnType returnType) { // 进如一个新的函数的作用域
        symbolTableCount += 1;
        SymbolTable newSymbolTable = new SymbolTable(symbolTableCount, currentSymbolTable, returnType);
        currentSymbolTable.sonScopeSymbolTables.put(newSymbolTable.scopeNum, newSymbolTable);
        currentSymbolTable = newSymbolTable;
        symbolTableList.add(currentSymbolTable);
        return currentSymbolTable;
    }

    public static SymbolTable backToUpperScope() {
        currentSymbolTable = currentSymbolTable.parentScopeSymbolTable;
        return currentSymbolTable;
    }

    public static void jumpToSymbolTableByScopeNum(int scopeNum) {
        if (currentSymbolTable.sonScopeSymbolTables.containsKey(scopeNum)) {
            currentSymbolTable = currentSymbolTable.sonScopeSymbolTables.get(scopeNum);
        }
    }

    public static void printSymbolTable(FileWriter debugWriter) throws IOException {
        //System.out.println("符号表数量为 " + symbolTableList.size());
        for (SymbolTable symbolTable : symbolTableList) {
            for (Symbol symbol : symbolTable.symbols) {
                debugWriter.write(symbol.toString() + "\n");
            }
        }
    }

}
