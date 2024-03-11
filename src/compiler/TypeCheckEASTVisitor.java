package compiler;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;

import java.sql.Ref;

import static compiler.TypeRels.*;

//visitNode(n) fa il type checking di un Node n e ritorna:
//- per una espressione, il suo tipo (oggetto BoolTypeNode o IntTypeNode)
//- per una dichiarazione, "null"; controlla la correttezza interna della dichiarazione
//(- per un tipo: "null"; controlla che il tipo non sia incompleto)
//
//visitSTentry(s) ritorna, per una STentry s, il tipo contenuto al suo interno
public class TypeCheckEASTVisitor extends BaseEASTVisitor<TypeNode,TypeException> {

	TypeCheckEASTVisitor() { super(true); } // enables incomplete tree exceptions 
	TypeCheckEASTVisitor(boolean debug) { super(true,debug); } // enables print for debugging

	//checks that a type object is visitable (not incomplete) 
	private TypeNode ckvisit(TypeNode t) throws TypeException {
		visit(t);
		return t;
	} 

	@Override
	public TypeNode visitNode(ProgLetInNode n) throws TypeException {
		if (print) printNode(n);
		// visito le dichiarazioni delle classi
		for (Node cl : n.classlist)
			try {
				visit(cl);
			} catch (IncomplException e) {
			}
		// visito le dichiarazioni delle classi
		for (Node dec : n.declist)
			try {
				visit(dec);
			} catch (IncomplException e) { 
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration: " + e.text);
			}
		return visit(n.exp);
	}

	@Override
	public TypeNode visitNode(ProgNode n) throws TypeException {
		if (print) printNode(n);
		return visit(n.exp);
	}



	@Override
	public TypeNode visitNode(FunNode n) throws TypeException {
		if (print) printNode(n,n.id);
		for (Node dec : n.declist)
			try {
				visit(dec);
			} catch (IncomplException e) { 
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration: " + e.text);
			}
		if ( !isSubtype(visit(n.exp),ckvisit(n.retType)) ) 
			throw new TypeException("Wrong return type for function " + n.id,n.getLine());
		return null;
	}

	@Override
	public TypeNode visitNode(VarNode n) throws TypeException {
		if (print) printNode(n,n.id);

		TypeNode t1 =  ckvisit(n.getType());
		TypeNode t2 = visit(n.exp);

		// check che t1 e t2 siano istanze di RefType (siano entrambi riferimenti ad oggetti)
		if(t1 instanceof RefTypeNode && t2 instanceof RefTypeNode) {
			// in caso positivo controlliamo che si riferiscano alla stessa classe
			if ( !isSameClass((RefTypeNode)t2,(RefTypeNode) t1) )
				throw new TypeException("Incompatible class for variable " + n.id,n.getLine());
		}

		// altrimenti controlliamo che tra i due tipi ci sia una relazione di subtyping
		if ( !isSubtype(t2,ckvisit(n.getType())) )
			throw new TypeException("Incompatible value for variable " + n.id,n.getLine());
		return null;
	}

	@Override
	public TypeNode visitNode(MethodNode n) throws TypeException {
		if (print) printNode(n,n.id);
		for (Node dec : n.declist)
			try {
				// per ogni dichiarazione presente nel body effettuo una verifica di tipo
				visit(dec);
			} catch (IncomplException e) {
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration: " + e.text);
			}
		// infine controlliamo il tipo di ritorno che sia compatibile
		if ( !isSubtype(visit(n.exp),ckvisit(n.retType)) )
			throw new TypeException("Wrong return type for function " + n.id,n.getLine());
		return null;
	}

	@Override
	public TypeNode visitNode(ClassNode n) throws TypeException {
		if (print) printNode(n,n.id);
		for (Node method : n.methodlist)
			try {
				// controlliamo i tipi dei metodi presenti all'interno della classe
				visit(method);
			} catch (IncomplException e) {
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration of a method: " + e.text);
			}
		return null;

	}

	@Override
	public TypeNode visitNode(PrintNode n) throws TypeException {
		if (print) printNode(n);
		return visit(n.exp);
	}

	@Override
	public TypeNode visitNode(IfNode n) throws TypeException {
		if (print) printNode(n);
		// controlliamo che la condizione sia booleana
		if ( !(isSubtype(visit(n.cond), new BoolTypeNode())) )
			throw new TypeException("Non boolean condition in if",n.getLine());
		TypeNode t = visit(n.th);
		TypeNode e = visit(n.el);
		// verifico le relazioni di subtyping tra il risultato del ramo else con quello del ramo then
		if (isSubtype(t, e)) return e;
		if (isSubtype(e, t)) return t;
		throw new TypeException("Incompatible types in then-else branches",n.getLine());
	}

	@Override
	public TypeNode visitNode(EqualNode n) throws TypeException {
		if (print) printNode(n);
		TypeNode l = visit(n.left);
		TypeNode r = visit(n.right);
		// verifico che i due operandi siano in relazione di subtyping
		if ( !(isSubtype(l, r) || isSubtype(r, l)) )
			throw new TypeException("Incompatible types in equal",n.getLine());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(TimesNode n) throws TypeException {
		if (print) printNode(n);
		// verifico che i due operandi siano in relazione di subtyping col tipo intero
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in multiplication",n.getLine());
		return new IntTypeNode();
	}

	@Override
	public TypeNode visitNode(PlusNode n) throws TypeException {
		if (print) printNode(n);
		// verifico che i due operandi siano in relazione di subtyping col tipo intero
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in sum",n.getLine());
		return new IntTypeNode();
	}

	@Override
	public TypeNode visitNode(NotNode n) throws TypeException {
		if (print) printNode(n);
		// verifico che l'operando siano in relazione di subtyping col tipo booleano
		if ( !(isSubtype(visit(n.node), new BoolTypeNode())))
			throw new TypeException("Non boolean in not",n.getLine());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(CallNode n) throws TypeException {
		if (print) printNode(n,n.id);
		TypeNode t = visit(n.entry);
		ArrowTypeNode at;
		// controllo che la chiamata sia relativa ad un metodo di una classe o di una funzione
		if ( !(t instanceof ArrowTypeNode) )
			throw new TypeException("Invocation of a non-function "+n.id,n.getLine());
		else
			at = (ArrowTypeNode) t;

		// controllo sul numero e sul tipo di parametri
		if ( !(at.parlist.size() == n.arglist.size()) )
			throw new TypeException("Wrong number of parameters in the invocation of "+n.id,n.getLine());
		for (int i = 0; i < n.arglist.size(); i++)
			if ( !(isSubtype(visit(n.arglist.get(i)),at.parlist.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.id,n.getLine());
		return at.ret;
	}

	@Override
	public TypeNode visitNode(LesseqNode n) throws TypeException {
		if (print) printNode(n);
		// verifico che i due operandi siano in relazione di subtyping col tipo intero
		if ( !(isSubtype(visit(n.left), new IntTypeNode())))
			throw new TypeException("Non integers in lesseq, type is " + visit(n.left),n.getLine());
		// verifico che i due operandi siano in relazione di subtyping col tipo intero
		if ( !(isSubtype(visit(n.right), new IntTypeNode())))
			throw new TypeException("Non integers in lesseq, type is " + visit(n.right),n.getLine());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(GreqNode n) throws TypeException {
		if (print) printNode(n);
		// verifico che i due operandi siano in relazione di subtyping col tipo intero
		if ( !(isSubtype(visit(n.left), new IntTypeNode())))
			throw new TypeException("Non integers in greq",n.getLine());
		// verifico che i due operandi siano in relazione di subtyping col tipo intero
		if ( !(isSubtype(visit(n.right), new IntTypeNode())))
			throw new TypeException("Non integers in greq",n.getLine());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(OrNode n) throws TypeException {
		if (print) printNode(n);
		// verifico che i due operandi siano in relazione di subtyping col tipo booleano
		if ( !(isSubtype(visit(n.left), new BoolTypeNode())))
			throw new TypeException("Non boolean in or",n.getLine());
		// verifico che i due operandi siano in relazione di subtyping col tipo booleano
		if ( !(isSubtype(visit(n.right), new BoolTypeNode())))
			throw new TypeException("Non boolean in or",n.getLine());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(AndNode n) throws TypeException {
		if (print) printNode(n);
		// verifico che i due operandi siano in relazione di subtyping col tipo booleano
		if ( !(isSubtype(visit(n.left), new BoolTypeNode())))
			throw new TypeException("Non boolean in and",n.getLine());
		// verifico che i due operandi siano in relazione di subtyping col tipo booleano
		if ( !(isSubtype(visit(n.right), new BoolTypeNode())))
			throw new TypeException("Non boolean in and",n.getLine());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(DivNode n) throws TypeException {
		if (print) printNode(n);
		// verifico che i due operandi siano in relazione di subtyping col tipo intero
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in div",n.getLine());
		return new IntTypeNode();
	}

	@Override
	public TypeNode visitNode(MinusNode n) throws TypeException {
		if (print) printNode(n);
		// verifico che i due operandi siano in relazione di subtyping col tipo intero
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in minus",n.getLine());
		return new IntTypeNode();
	}

	@Override
	public TypeNode visitNode(ClassCallNode n) throws TypeException {
		if (print) printNode(n,n.id);
		TypeNode t = visit(n.methodEntry);
		MethodTypeNode at;

		// verifico che la chiamata sia relativa ad un metodo
		if(t instanceof MethodTypeNode)
			at = ((MethodTypeNode) t);
		else
			throw new TypeException("Invocation of a non-method "+n.idMethod,n.getLine());

		// controllo il numero e il tipo di parametri
		if ( !(at.fun.parlist.size() == n.arglist.size()) )
			throw new TypeException("Wrong number of parameters in the invocation of "+n.id,n.getLine());
		for (int i = 0; i < n.arglist.size(); i++)
			if ( !(isSubtype(visit(n.arglist.get(i)),at.fun.parlist.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.id,n.getLine());
		return at.fun.ret;
	}

	@Override
	public TypeNode visitNode(EmptyNode nullNode) throws TypeException {
		return new EmptyTypeNode();
	}

	@Override
	public TypeNode visitNode(NewNode n) throws TypeException {
		if (print) printNode(n,n.id);
		TypeNode t = visit(n.entry);
		ClassTypeNode at;

		// controllo che il tipo sia un ClassTypeNode (classe da istanziare)
		if(t instanceof ClassTypeNode)
			at = (ClassTypeNode) t;
		else
			throw new TypeException("Invocation of a non-class ID "+n.id,n.getLine());

		// controllo che il numero e tipo di campi siano quelli previsti dalla classe
		if ( !(at.allFields.size() == n.arglist.size()) )
			throw new TypeException("Wrong number of parameters in the invocation of "+n.id,n.getLine());
		for (int i = 0; i < n.arglist.size(); i++)
			if ( !(isSubtype(visit(n.arglist.get(i)),at.allFields.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.id,n.getLine());

		return new RefTypeNode(n.id);
	}

	@Override
	public TypeNode visitNode(IdNode n) throws TypeException {
		if (print) printNode(n,n.id);
		TypeNode t = visit(n.entry);
		// controllo che l'id sia un metodo, una funzione o una classe
		if (t instanceof ArrowTypeNode || t instanceof MethodTypeNode || t instanceof ClassTypeNode)
			throw new TypeException("Wrong usage of function identifier " + n.id,n.getLine());

		return t;
	}

	@Override
	public TypeNode visitNode(BoolNode n) {
		if (print) printNode(n,n.val.toString());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(IntNode n) {
		if (print) printNode(n,n.val.toString());
		return new IntTypeNode();
	}
	
	@Override
	public TypeNode visitNode(ArrowTypeNode n) throws TypeException {
		if (print) printNode(n);
		for (Node par: n.parlist) visit(par);
		visit(n.ret,"->"); //marks return type
		return null;
	}

	@Override
	public TypeNode visitNode(BoolTypeNode n) {
		if (print) printNode(n);
		return null;
	}

	@Override
	public TypeNode visitNode(RefTypeNode n) throws TypeException {
		if (print) printNode(n);
		return null;
	}

	@Override
	public TypeNode visitNode(IntTypeNode n) {
		if (print) printNode(n);
		return null;
	}

	@Override
	public TypeNode visitNode(MethodTypeNode n) throws TypeException {
		if (print) printNode(n);
		return null;
	}

	@Override
	public TypeNode visitNode(ClassTypeNode n) throws TypeException {
		if (print) printNode(n);
		return null;
	}

	@Override
	public TypeNode visitNode(EmptyTypeNode n) throws TypeException {
		if (print) printNode(n);
		return null;
	}

	// STentry (ritorna campo type)
	@Override
	public TypeNode visitSTentry(STentry entry) throws TypeException {
		if (print) printSTentry("type");
		return ckvisit(entry.type); 
	}

}