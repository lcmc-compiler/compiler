package compiler;

import compiler.AST.*;
import compiler.lib.*;

import java.sql.Ref;
import java.util.Objects;

public class TypeRels {

	// valuta se il tipo "a" e' <= al tipo "b", dove "a" e "b" sono tipi di base: IntTypeNode o BoolTypeNode
	public static boolean isSubtype(TypeNode a, TypeNode b) {
		return a.getClass().equals(b.getClass()) ||
				((a instanceof BoolTypeNode) && (b instanceof IntTypeNode)) ||
				((a instanceof EmptyTypeNode) && (b instanceof RefTypeNode));
	}

	public static boolean isSameClass(RefTypeNode a, RefTypeNode b) {
		return Objects.equals(a.classId, b.classId);
	}

}
