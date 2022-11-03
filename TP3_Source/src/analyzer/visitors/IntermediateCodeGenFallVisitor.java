package analyzer.visitors;

import analyzer.ast.*;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import sun.awt.Symbol;

import java.awt.*;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Vector;


/**
 * Created: 19-02-15
 * Last Changed: 20-10-6
 * Author: Félix Brunet & Doriane Olewicki
 *
 * Description: Ce visiteur explore l'AST et génère un code intermédiaire.
 */

public class IntermediateCodeGenFallVisitor implements ParserVisitor {

    //le m_writer est un Output_Stream connecter au fichier "result". c'est donc ce qui permet de print dans les fichiers
    //le code généré.
    private final PrintWriter m_writer;

    public IntermediateCodeGenFallVisitor(PrintWriter writer) {
        m_writer = writer;
    }
    public HashMap<String, IntermediateCodeGenVisitor.VarType> SymbolTable = new HashMap<>();

    private int id = 0;
    private int label = 0;
    /*
    génère une nouvelle variable temporaire qu'il est possible de print
    À noté qu'il serait possible de rentrer en conflit avec un nom de variable définit dans le programme.
    Par simplicité, dans ce tp, nous ne concidérerons pas cette possibilité, mais il faudrait un générateur de nom de
    variable beaucoup plus robuste dans un vrai compilateur.
     */
    private String genId() {
        return "_t" + id++;
    }

    //génère un nouveau Label qu'il est possible de print.
    private String genLabel() {
        return "_L" + label++;
    }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data)  {
        String first_label = genLabel();
        data = first_label;
        node.childrenAccept(this, data);
        m_writer.print(first_label+"\n");
        return data;
    }

    /*
    Code fournis pour remplir la table de symbole.
    Les déclarations ne sont plus utile dans le code à trois adresse.
    elle ne sont donc pas concervé.
     */
    @Override
    public Object visit(ASTDeclaration node, Object data) {
        ASTIdentifier id = (ASTIdentifier) node.jjtGetChild(0);
        IntermediateCodeGenVisitor.VarType t;
        if(node.getValue().equals("bool")) {
            t = IntermediateCodeGenVisitor.VarType.Bool;
        } else {
            t = IntermediateCodeGenVisitor.VarType.Number;
        }
        SymbolTable.put(id.getValue(), t);
        return data;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        String value = "";
        String first_label = (String) data;
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            String label = "";
            if (node.jjtGetNumChildren() > i+1) {
                label = genLabel();
                data = label;
            }else data = first_label;
            value = node.jjtGetChild(i).jjtAccept(this, data).toString();
            if (node.jjtGetNumChildren() > i+1) m_writer.print(label+"\n");
        }
        return value;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data).toString();
    }

    /*
    le If Stmt doit vérifier s'il à trois enfants pour savoir s'il s'agit d'un "if-then" ou d'un "if-then-else".
     */
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        String next = (String) data;
        String value = "";
        if (node.jjtGetNumChildren() == 2) {
            data = new BoolLabel("", next);
            System.out.println(node.jjtGetNumChildren());
            value = node.jjtGetChild(0).jjtAccept(this, data).toString();

            data = next;
            value = node.jjtGetChild(1).jjtAccept(this, data).toString();
        }
        if (node.jjtGetNumChildren() == 3)  {
            String _L2 = genLabel();
            data = new BoolLabel("", _L2);
            System.out.println(node.jjtGetNumChildren());
            value = node.jjtGetChild(0).jjtAccept(this, data).toString();

            data = next;
            value = node.jjtGetChild(1).jjtAccept(this, data).toString();
            m_writer.print("goto "+next+"\n");
            m_writer.print(_L2+"\n");
            value = node.jjtGetChild(2).jjtAccept(this, data).toString();
        }
        return value;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        String next = (String) data;
        String value = "";
        String _L1 = genLabel();

        System.out.println(node.jjtGetNumChildren());
        m_writer.print(_L1 + "\n");
        data = new BoolLabel("", next);
        value = node.jjtGetChild(0).jjtAccept(this, data).toString();

        data = _L1;
        value = node.jjtGetChild(1).jjtAccept(this, data).toString();

        m_writer.print("goto "+_L1+"\n");
        return value;
    }


    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        String next = (String) data;
        data = null;
        Boolean isBool = SymbolTable.get(((ASTIdentifier) node.jjtGetChild(0)).getValue()) == IntermediateCodeGenVisitor.VarType.Bool;
        String _L2 = "";
        if(isBool) {
            _L2 = genLabel();
            data = new BoolLabel("", _L2);
        }

        String id = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        String value = node.jjtGetChild(1).jjtAccept(this, data).toString();
        if(isBool) {
            m_writer.print(id+" = 1\n");
            m_writer.print("goto "+next+"\n");
            m_writer.print(_L2+"\n");
            m_writer.print(id+" = 0\n");
            return value;
        }

        m_writer.print(id+" = "+value+"\n");
        return value;
    }



    @Override
    public Object visit(ASTExpr node, Object data){
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    //Expression arithmétique
    /*
    Les expressions arithmétique add et mult fonctionne exactement de la même manière. c'est pourquoi
    il est plus simple de remplir cette fonction une fois pour avoir le résultat pour les deux noeuds.

    On peut bouclé sur "ops" ou sur node.jjtGetNumChildren(),
    la taille de ops sera toujours 1 de moins que la taille de jjtGetNumChildren
     */
    public Object codeExtAddMul(SimpleNode node, Object data, Vector<String> ops) {
        String _t = "";
        if(node.jjtGetNumChildren() == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data).toString();
        };

        _t = genId();
        String value0 = node.jjtGetChild(0).jjtAccept(this, data).toString();
        String value1 = node.jjtGetChild(1).jjtAccept(this, data).toString();
        m_writer.print(_t+" = "+value0+" "+ops.get(0)+" "+value1+"\n");

        return _t;
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    //UnaExpr est presque pareil au deux précédente. la plus grosse différence est qu'il ne va pas
    //chercher un deuxième noeud enfant pour avoir une valeur puisqu'il s'agit d'une opération unaire.
    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        String _t = "";
        String value = node.jjtGetChild(0).jjtAccept(this, data).toString();
        Vector ops = node.getOps();
        if (!ops.isEmpty()){
            for (int i = 0; i < ops.size(); i++) {
                String _old_t = _t;
                _t = this.genId();
                if(i == 0) m_writer.print(_t+" = - "+value+"\n");
                else m_writer.print(_t+" = - "+_old_t+"\n");
            }
        }else{
            _t = value;
        }

        return _t;
    }

    //expression logique
    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        String value = "";
        Vector ops = node.getOps();


        if(node.jjtGetNumChildren() == 1) {
            value = node.jjtGetChild(0).jjtAccept(this, data).toString();
            return value;
        };

        if (ops.get(0).equals("&&")) {
            node.jjtGetChild(0).jjtAccept(this, data).toString();
            return node.jjtGetChild(1).jjtAccept(this, data).toString();
        } else if (ops.get(0).equals("||")) {
            String old_L = ((BoolLabel) data).lFalse;
            String _L = genLabel();
            ((BoolLabel) data).lTrue = _L;
            ((BoolLabel) data).lFalse = "";
            node.jjtGetChild(0).jjtAccept(this, data).toString();
            ((BoolLabel) data).lTrue = "";
            ((BoolLabel) data).lFalse = old_L;
            value = node.jjtGetChild(1).jjtAccept(this, data).toString();
            m_writer.print(_L+"\n");
        }

        return value;
    }





    @Override
    public Object visit(ASTCompExpr node, Object data) {
        if(node.jjtGetNumChildren() == 1) {
            String value = node.jjtGetChild(0).jjtAccept(this, data).toString();
            return value;
        };

        String value1 = node.jjtGetChild(0).jjtAccept(this, data).toString();
        String value2 = node.jjtGetChild(1).jjtAccept(this, data).toString();
        m_writer.print("ifFalse "+value1+" "+node.getValue()+" "+value2+" goto "+((BoolLabel) data).lFalse+"\n");
        return value1;
    }


    /*
    Même si on peut y avoir un grand nombre d'opération, celle-ci s'annullent entre elle.
    il est donc intéressant de vérifier si le nombre d'opération est pair ou impaire.
    Si le nombre d'opération est pair, on peut simplement ignorer ce noeud.
     */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        if(node.getOps().size() % 2 == 1){
            String lFalse = ((BoolLabel) data).lFalse;
            ((BoolLabel) data).lFalse = ((BoolLabel) data).lTrue;
            ((BoolLabel) data).lTrue = lFalse;
        }
        String value = node.jjtGetChild(0).jjtAccept(this, data).toString();
        return value;
    }

    @Override
    public Object visit(ASTGenValue node, Object data) {
        String value = node.jjtGetChild(0).jjtAccept(this, data).toString();
        return value;
    }

    /*
    BoolValue ne peut pas simplement retourné sa valeur à son parent contrairement à GenValue et IntValue,
    Il doit plutôt généré des Goto direct, selon sa valeur.
     */
    @Override
    public Object visit(ASTBoolValue node, Object data) {
        if (node.getValue()) {
            if(((BoolLabel) data).lTrue == "") return ((BoolLabel) data).lTrue;
            m_writer.print("goto "+((BoolLabel) data).lTrue+"\n");
            return ((BoolLabel) data).lTrue;
        }
        else {
            if(((BoolLabel) data).lFalse == "") return ((BoolLabel) data).lFalse;
            m_writer.print("goto "+((BoolLabel) data).lFalse+"\n");
            return ((BoolLabel) data).lFalse;
        }
    }


    /*
    si le type de la variable est booléenne, il faudra généré des goto ici.
    le truc est de faire un "if value == 1 goto Label".
    en effet, la structure "if valeurBool goto Label" n'existe pas dans la syntaxe du code à trois adresse.
     */
    @Override
    public Object visit(ASTIdentifier node, Object data) {
        BoolLabel b = (BoolLabel) data;
        String value = node.getValue();
        if(SymbolTable.get(value) == IntermediateCodeGenVisitor.VarType.Bool) {
            if(b.lTrue == "") {
                m_writer.print("ifFalse "+value+" == 1 goto "+b.lFalse+"\n");
            } else {
                m_writer.print("if "+value+" == 1 goto "+b.lTrue+"\n");
            }
        }
        return value;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return Integer.toString(node.getValue());
    }


    @Override
    public Object visit(ASTSwitchStmt node, Object data) {

        String values[] = new String[node.jjtGetNumChildren()-1];
        String case_L[] = new String[node.jjtGetNumChildren()-1];
        String value;
        String _L = genLabel();

        m_writer.print("goto "+_L+"\n");
        value = node.jjtGetChild(0).jjtAccept(this, null).toString();
        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            String returned[] = (String[]) node.jjtGetChild(i).jjtAccept(this, data);
            values[i-1] = returned[0];
            case_L[i-1] = returned[1];
        }
        m_writer.print(_L+"\n");
        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            if (values[i-1] == "") m_writer.print("goto "+case_L[i-1]+"\n");
            else m_writer.print("if "+value+" == "+values[i-1]+" goto "+case_L[i-1]+"\n");
        }
        return value;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        String next = (String) data;
        data = null;
        String _L = genLabel();
        m_writer.print(_L+"\n");
        String value = node.jjtGetChild(0).jjtAccept(this, data).toString();
        node.jjtGetChild(1).jjtAccept(this, data);
        m_writer.print("goto "+next+"\n");
        return new String[]{value, _L};
    }

    @Override
    public Object visit(ASTDefaultStmt node, Object data) {
        String next = (String) data;
        data = null;
        String _L = genLabel();
        m_writer.print(_L+"\n");
        String value = "";
        node.jjtGetChild(0).jjtAccept(this, data);
        m_writer.print("goto "+next+"\n");
        return new String[]{value, _L};
    }

    //des outils pour vous simplifier la vie et vous enligner dans le travail
    public enum VarType {
        Bool,
        Number
    }

    //utile surtout pour envoyé de l'informations au enfant des expressions logiques.
    private class BoolLabel {
        public String lTrue = null;
        public String lFalse = null;

        public BoolLabel(String t, String f) {
            lTrue = t;
            lFalse = f;
        }
    }

}
