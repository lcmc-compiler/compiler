package compiler;

import java.util.*;
import java.util.stream.Collectors;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;

public class SymbolTableASTVisitor extends BaseASTVisitor<Void,VoidException> {
	
	private List<Map<String, STentry>> symTable = new ArrayList<>();

	private Map<String, Map<String, STentry>> classTable = new HashMap<>();
	private int nestingLevel=0; // current nesting level
	private int decOffset=-2; // counter for offset of local declarations at current nesting level 
	int stErrors=0;

	SymbolTableASTVisitor() {}
	SymbolTableASTVisitor(boolean debug) {super(debug);} // enables print for debugging

	private STentry stLookup(String id) {
		int j = nestingLevel;
		STentry entry = null;
		while (j >= 0 && entry == null) 
			entry = symTable.get(j--).get(id);	
		return entry;
	}

	@Override
	public Void visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		Map<String, STentry> hm = new HashMap<>();
		symTable.add(hm);
		for (Node dec : n.classlist) visit(dec);
	    for (Node dec : n.declist) visit(dec);
		visit(n.exp);
		symTable.remove(0);
		return null;
	}

	@Override
	public Void visitNode(ProgNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}
	
	@Override
	public Void visitNode(FunNode n) {
		if (print) printNode(n);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		List<TypeNode> parTypes = new ArrayList<>();  
		for (ParNode par : n.parlist) parTypes.add(par.getType()); 
		STentry entry = new STentry(nestingLevel, new ArrowTypeNode(parTypes,n.retType),decOffset--);
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
		//creare una nuova hashmap per la symTable
		nestingLevel++;
		Map<String, STentry> hmn = new HashMap<>();
		symTable.add(hmn);
		int prevNLDecOffset=decOffset; // stores counter for offset of declarations at previous nesting level 
		decOffset=-2;
		
		int parOffset=1;
		for (ParNode par : n.parlist)
			if (hmn.put(par.id, new STentry(nestingLevel,par.getType(),parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		for (Node dec : n.declist) visit(dec);
		visit(n.exp);
		//rimuovere la hashmap corrente poiche' esco dallo scope               
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level 
		return null;
	}

	@Override
	public Void visitNode(ClassNode n) throws VoidException {
		if (print) printNode(n);
		System.out.println("VISITO CLASSE " + n.id);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		ArrayList<TypeNode> fields = new ArrayList<>();
		ArrayList<MethodTypeNode> methods = new ArrayList<>();

		ClassTypeNode classTypeNode = new ClassTypeNode(fields, methods);

		// aggiungo il tipo della classe all'interno della rispettiva STEntry
		STentry entry = new STentry(nestingLevel, classTypeNode, decOffset--);
		if (hm.put(n.id, entry) != null) {
			System.out.println("Class id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}

		// creare una nuova hashmap per la virtual table da inserire anche all'interno della symbol table già creata
		nestingLevel++;
		// la virtual table contiene tutte le definizioni di campi e metodi relativi al nome di una classe
		Map<String, STentry> virtualTable = new HashMap<>();
		classTable.put(n.id, virtualTable);
		symTable.add(virtualTable);
		int prevNLDecOffset=decOffset; // stores counter for offset of declarations at previous nesting level
		// nel layout deciso a priori, decOffset indica il valore
		// di offset per i metodi della classe, che per definizione parte da 0, per creare correttamente il layout dello HEAP
		// da usare in fase runtime di creazione di oggetti
		decOffset=0;

		int fieldOffset=-1;
		// visita di tutti i campi dichiarati per la classe, in modo da verificare che non ci siano conflitti di dichiarazioni già esistenti
		for (FieldNode field : n.fieldlist) {
			if (virtualTable.put(field.id, new STentry(nestingLevel, field.getType(), fieldOffset--)) != null) {
				System.out.println("Field id " + field.id + " at line " + n.getLine() + " already declared");
				stErrors++;
			}
			// aggiorno la lista fields relativa al ClassTypeNode
			fields.add(field.getType());
		}

		// visita di tutti i metodi dichiarati all'interno della classe
		// TODO l'errore è qui da qualche parte
		for (MethodNode method : n.methodlist) {
			visit(method);
			/*if (virtualTable.put(method.id, new STentry(nestingLevel, method.getType(), decOffset)) != null) {
				System.out.println("Method id " + method.id + " at line " + n.getLine() + " already declared");
				stErrors++;
			}*/
			method.offset = decOffset;

			// aggiorno la lista methods relativa al ClassTypeNode
			methods.add(new MethodTypeNode(
					new ArrowTypeNode(method.parlist.stream().map(DecNode::getType).collect(Collectors.toList()), method.retType)
			));
		}

		//rimuovere la hashmap corrente poiche' esco dallo scope
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level
		return null;
	}

	@Override
	public Void visitNode(MethodNode n) throws VoidException {
		if (print) printNode(n);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		List<TypeNode> parTypes = new ArrayList<>();
		for (ParNode par : n.parlist) parTypes.add(par.getType());
		n.offset = decOffset;
		STentry entry = new STentry(nestingLevel, new MethodTypeNode(new ArrowTypeNode(parTypes,n.retType)),decOffset++);
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Method id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
		//creare una nuova hashmap per la symTable
		nestingLevel++;
		Map<String, STentry> hmn = new HashMap<>();
		symTable.add(hmn);
		int prevNLDecOffset=decOffset; // stores counter for offset of declarations at previous nesting level
		decOffset=1; // non -2

		for (ParNode par : n.parlist)
			if (hmn.put(par.id, new STentry(nestingLevel,par.getType(),decOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		for (Node dec : n.declist) visit(dec);
		visit(n.exp);
		//rimuovere la hashmap corrente poiche' esco dallo scope
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level
		return null;
	}

	@Override
	public Void visitNode(ClassCallNode n) throws VoidException {
		if (print) printNode(n);

		STentry entry = stLookup(n.id);
		if (entry == null) {
			System.out.println("Var id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
			return null;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}

		// ci serve il RefTypeNode
		String classId = ((RefTypeNode)entry.type).classId;
		System.out.println("(CLASSCALLNODE) ID var: " + n.id + " - Type var: " + classId);
		System.out.println("ID METHOD: " + n.idMethod);
		STentry methodEntry = classTable.get(classId).get(n.idMethod);
		n.methodEntry = methodEntry;

		for (Node arg : n.arglist) visit(arg);

		return null;
	}

	@Override
	public Void visitNode(VarNode n) {
		if (print) printNode(n);
		visit(n.exp);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		System.out.println("ID var: " + n.id + " - Type var: " + n.getType());
		STentry entry = new STentry(nestingLevel,n.getType(),decOffset--);
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Var id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
		return null;
	}

	@Override
	public Void visitNode(NewNode n) throws VoidException {
		if (print) printNode(n);
		Map<String, STentry> methodEntry = classTable.get(n.id);
		STentry entry = symTable.get(0).get(n.id);
		if(methodEntry == null || entry == null) {
			System.out.println("Class id " + n.id + " at line "+ n.getLine() +" not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}

	@Override
	public Void visitNode(PrintNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}

	@Override
	public Void visitNode(IfNode n) {
		if (print) printNode(n);
		visit(n.cond);
		visit(n.th);
		visit(n.el);
		return null;
	}

	@Override
	public Void visitNode(EmptyNode n) throws VoidException {
		if (print) printNode(n);
		return null;
	}

	@Override
	public Void visitNode(LesseqNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(GreqNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(OrNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(AndNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(DivNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(MinusNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(EqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(TimesNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(PlusNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(CallNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id);
		if (entry == null) {
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}

	@Override
	public Void visitNode(IdNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id);
		System.out.println(entry.type);
		System.out.println(entry.offset);

		if (entry == null) {
			System.out.println("Var or Par id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		return null;
	}

	@Override
	public Void visitNode(BoolNode n) {
		if (print) printNode(n, n.val.toString());
		return null;
	}

	@Override
	public Void visitNode(IntNode n) {
		if (print) printNode(n, n.val.toString());
		return null;
	}

	@Override
	public Void visitNode(NotNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.node);
		return null;
	}



}
