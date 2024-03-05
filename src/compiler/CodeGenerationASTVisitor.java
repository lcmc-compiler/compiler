package compiler;

import compiler.AST.*;
import compiler.lib.*;
import compiler.exc.*;
import svm.ExecuteVM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static compiler.lib.FOOLlib.*;
import static svm.ExecuteVM.MEMSIZE;

public class CodeGenerationASTVisitor extends BaseASTVisitor<String, VoidException> {

	//List<String> dispatchTables = new ArrayList<>();

  CodeGenerationASTVisitor() {}
  CodeGenerationASTVisitor(boolean debug) {super(false,debug);} //enables print for debugging

	@Override
	public String visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		String classCode = null;
		String declCode = null;
		for (Node cl : n.classlist) classCode=nlJoin(classCode,visit(cl));
		for (Node dec : n.declist) declCode=nlJoin(declCode,visit(dec));
		return nlJoin(
			"push 0",
			classCode,
			//"/* end class code */",
			declCode, // generate code for declarations (allocation)
			//"/* end decl code */",
			visit(n.exp),
			"halt",
			getCode()
		);
	}

	@Override
	public String visitNode(ProgNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"halt"
		);
	}

	@Override
	public String visitNode(LesseqNode n) throws VoidException {
		String l1 = freshLabel();
		String l2 = freshLabel();
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"bleq " + l1,
				"push 0",
				"b " + l2,
				l1 + ":",
				"push 1",
				l2 + ":"
		);
	}

	@Override
	public String visitNode(GreqNode n) throws VoidException {
		String l1 = freshLabel();
		String l2 = freshLabel();
		String l3 = freshLabel();
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"beq " + l1,

				visit(n.left),
				visit(n.right),
				"bleq " + l2,

				l1 + ":", // uguale
				"push 1",
				"b " + l3,

				l2 + ":", // minore
				"push 0",

				l3 + ":"
		);
	}

	@Override
	public String visitNode(OrNode n) throws VoidException {
		if (print) printNode(n);
		/*String left = visit(n.left);
		String right = visit(n.right);
		String result = "0";
		if(left.equals("1"))
			result = "1";
		else if(right.equals("1"))
			result = "1";
		*/
		String label1 = freshLabel();
		String label2 = freshLabel();

		/*return nlJoin(
				"push " + result
		);*/
		return nlJoin(
				visit(n.right),
				visit(n.left),
				"bleq " + label1,
				visit(n.right),
				"b " + label2,
				label1 + ":",
				visit(n.left),
				label2 + ":"
		);
	}

	@Override
	public String visitNode(AndNode n) throws VoidException {
		if (print) printNode(n);

		return nlJoin(
				visit(n.left),
				visit(n.right),
				"mult"
		);
	}

	@Override
	public String visitNode(DivNode n) throws VoidException {
		if (print) printNode(n);
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"div"
		);
	}

	@Override
	public String visitNode(MinusNode n) throws VoidException {
		if (print) printNode(n);
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"sub"
		);
	}

	@Override
	public String visitNode(FunNode n) {
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
		}
		for (int i=0;i<n.parlist.size();i++) popParl = nlJoin(popParl,"pop");
		String funl = freshFunLabel();
		putCode(
			nlJoin(
				funl+":",
				"cfp", // set $fp to $sp value
				"lra", // load $ra value
				declCode, // generate code for local declarations (they use the new $fp!!!)
				visit(n.exp), // generate code for function body expression
				"stm", // set $tm to popped value (function result)
				popDecl, // remove local declarations from stack
				"sra", // set $ra to popped value
				"pop", // remove Access Link from stack
				popParl, // remove parameters from stack
				"sfp", // set $fp to popped value (Control Link)
				"ltm", // load $tm value (function result)
				"lra", // load $ra value
				"js"  // jump to to popped address
			)
		);
		return "push "+funl;		
	}

	@Override
	public String visitNode(MethodNode n) throws VoidException {
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
		}
		for (int i=0;i<n.parlist.size();i++) popParl = nlJoin(popParl,"pop");
		putCode(
				nlJoin(
						//"/* method " + n.id + " declaration */",
						n.label+":",
						"cfp", // set $fp to $sp value
						"lra", // load $ra value
						declCode, // generate code for local declarations (they use the new $fp!!!)
						visit(n.exp), // generate code for function body expression
						"stm", // set $tm to popped value (function result)
						popDecl, // remove local declarations from stack
						"sra", // set $ra to popped value
						"pop", // remove Access Link from stack
						popParl, // remove parameters from stack
						"sfp", // set $fp to popped value (Control Link)
						"ltm", // load $tm value (function result)
						"lra", // load $ra value
						"js"  // jump to popped address
				)
		);
		return null;
	}

	@Override
	public String visitNode(NotNode n) throws VoidException {
		if (print) printNode(n);
		return nlJoin(
				// per invertire un booleano (0 e 1) eseguo la sottrazione per 1
				// 1 è true 0 è false
				"push 1",
				visit(n.node),
				"sub"
		);
	}



	@Override
	public String visitNode(ClassNode n) throws VoidException {
		if (print) printNode(n,n.id);
		ArrayList<String> dispatchTable = new ArrayList<>();
		for(MethodNode method : n.methodlist) {
			String freshLabel = freshFunLabel();
			dispatchTable.add(freshLabel);
			method.label = freshLabel;
			visit(method);
		}
		String labels = "";
		for (String methodLabel : dispatchTable) {
			labels = nlJoin(labels,
					"/* method " + methodLabel + "*/",
					"push " +methodLabel, // pusho la label sullo stack
					"lhp", // pusho hp su stack
					"sw", // poppo due valori: hp e label presenti su stack e memorizzo label in indirizzo presente in hp
					"push 1", // pusho 1 per incrementare hp
					"lhp", // pusho hp per eseguire la somma con 1
					"add", // poppo due valori e li sommo: hp + 1
					"shp" // memorizzo il risultato della somma in hp (hp = hp + 1)
			);
		}

		return nlJoin(
				//"/* class " + n.id + " declaration */",
				"lhp", // push the content of hp register to the top of the stack
				labels
		);
	}

	@Override
	public String visitNode(EmptyNode n) throws VoidException {
		if (print) printNode(n);
		return "push -1";
	}

	@Override
	public String visitNode(ClassCallNode n) throws VoidException {
		if (print) printNode(n,n.id);

		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		return nlJoin(
				//"/* method " + n.idMethod + " recall */",
				"lfp", // load Control Link (pointer to frame of function "id" caller)
				argCode, // generate code for argument expressions in reversed order
				"lfp", getAR, // retrieve address of frame containing "id" declaration
				// by following the static chain (of Access Links)
				"push " + n.entry.offset, // address of object's dispatch pointer
				"add",
				"lw",

				"stm", // set $tm to popped value (with the aim of duplicating top of stack)
				"ltm", // load Access Link (pointer to frame of function "id" declaration)
				"ltm", // duplicate top of stack
				"lw",

				// TODO l'errore è qui: dopo lw cerca in indirizzo -1
				"push "+n.methodEntry.offset,
				"add", // compute address of "id" declaration
				"lw", // load address of "id" function
				"js"  // jump to popped address (saving address of subsequent instruction in $ra)
		);
	}

	@Override
	public String visitNode(NewNode n) throws VoidException {
		if (print) printNode(n,n.id);

		String argCode = null;
		for (int i = 0; i < n.arglist.size(); i++)
			argCode=nlJoin(argCode,visit(n.arglist.get(i))
					,"/* campo classe */"
			);

		String pushOnHeapCode = null;
		for (int i=0;i<n.arglist.size();i++) {
			pushOnHeapCode = nlJoin(pushOnHeapCode,
					"lhp", // pusho hp su stack
					"sw", // poppo due valori: hp e label presenti su stack e memorizzo label in indirizzo presente in hp

					"lhp", // pusho 1 per incrementare hp
					"push 1", // pusho hp per eseguire la somma con 1
					"add", // poppo due valori e li sommo: hp + 1
					"shp" // memorizzo il risultato della somma in hp (hp = hp + 1)
			);
		}

		return nlJoin(
				argCode,
				pushOnHeapCode,
				"push " + MEMSIZE,
				"push " + n.entry.offset,
				"add",
				"lw",


				"lhp",
				"sw",

				"lhp",

				"lhp", // pusho hp per eseguire la somma con 1
				"push 1", // pusho 1 per incrementare hp
				"add", // poppo due valori e li sommo: hp + 1
				"shp" // memorizzo il risultato della somma in hp (hp = hp + 1)
		);

	}

	@Override
	public String visitNode(VarNode n) {
		if (print) printNode(n,n.id);
		return visit(n.exp);
	}

	@Override
	public String visitNode(PrintNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"print"
		);
	}

	@Override
	public String visitNode(IfNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();		
		return nlJoin(
			visit(n.cond),
			"push 1",
			"beq "+l1,
			visit(n.el),
			"b "+l2,
			l1+":",
			visit(n.th),
			l2+":"
		);
	}

	@Override
	public String visitNode(EqualNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"beq "+l1,
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}

	@Override
	public String visitNode(TimesNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"mult"
		);	
	}

	@Override
	public String visitNode(PlusNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"add"				
		);
	}



	@Override
	public String visitNode(CallNode n) {
		if (print) printNode(n,n.id);
		TypeNode t = n.entry.type;
		if(t instanceof MethodTypeNode) {
			//offset = dispatchTables.get(n.entry.offset);
		}
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--)
			argCode=nlJoin(argCode,visit(n.arglist.get(i)));

		for (int i = 0;i<n.nl-n.entry.nl;i++)
			getAR=nlJoin(getAR,"lw");

		return nlJoin(
			"lfp", // load Control Link (pointer to frame of function "id" caller)
			argCode, // generate code for argument expressions in reversed order
			"lfp", getAR, // retrieve address of frame containing "id" declaration
                          // by following the static chain (of Access Links)
            "stm", // set $tm to popped value (with the aim of duplicating top of stack)
            "ltm", // load Access Link (pointer to frame of function "id" declaration)
            "ltm", // duplicate top of stack

            "push " + n.entry.offset,
			"add", // compute address of "id" declaration
			"lw", // load address of "id" function
            "js"  // jump to popped address (saving address of subsequent instruction in $ra)
		);
	}

	@Override
	public String visitNode(IdNode n) {
		if (print) printNode(n,n.id);
		String getAR = null;
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		return nlJoin(
			"lfp", getAR, // retrieve address of frame containing "id" declaration
			              // by following the static chain (of Access Links)
			"push "+n.entry.offset,
			"add", // compute address of "id" declaration
			"lw" // load value of "id" variable
		);
	}

	@Override
	public String visitNode(BoolNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+(n.val?1:0);
	}

	@Override
	public String visitNode(IntNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+n.val;
	}
}