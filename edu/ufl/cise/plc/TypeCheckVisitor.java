package edu.ufl.cise.plc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.params.provider.NullEnum;

import edu.ufl.cise.plc.IToken.Kind;
import edu.ufl.cise.plc.ast.ASTNode;
import edu.ufl.cise.plc.ast.ASTVisitor;
import edu.ufl.cise.plc.ast.AssignmentStatement;
import edu.ufl.cise.plc.ast.BinaryExpr;
import edu.ufl.cise.plc.ast.BooleanLitExpr;
import edu.ufl.cise.plc.ast.ColorConstExpr;
import edu.ufl.cise.plc.ast.ColorExpr;
import edu.ufl.cise.plc.ast.ConditionalExpr;
import edu.ufl.cise.plc.ast.ConsoleExpr;
import edu.ufl.cise.plc.ast.Declaration;
import edu.ufl.cise.plc.ast.Dimension;
import edu.ufl.cise.plc.ast.Expr;
import edu.ufl.cise.plc.ast.FloatLitExpr;
import edu.ufl.cise.plc.ast.IdentExpr;
import edu.ufl.cise.plc.ast.IntLitExpr;
import edu.ufl.cise.plc.ast.NameDef;
import edu.ufl.cise.plc.ast.NameDefWithDim;
import edu.ufl.cise.plc.ast.PixelSelector;
import edu.ufl.cise.plc.ast.Program;
import edu.ufl.cise.plc.ast.ReadStatement;
import edu.ufl.cise.plc.ast.ReturnStatement;
import edu.ufl.cise.plc.ast.StringLitExpr;
import edu.ufl.cise.plc.ast.Types.Type;
import edu.ufl.cise.plc.ast.UnaryExpr;
import edu.ufl.cise.plc.ast.Statement;
import edu.ufl.cise.plc.ast.UnaryExprPostfix;
import edu.ufl.cise.plc.ast.VarDeclaration;
import edu.ufl.cise.plc.ast.WriteStatement;

import static edu.ufl.cise.plc.ast.Types.Type.*;

public class TypeCheckVisitor implements ASTVisitor {

	SymbolTable symbolTable = new SymbolTable();
	Program root;

	record Pair<T0, T1> (T0 t0, T1 t1) {
	}; // may be useful for constructing lookup tables.

	private void check(boolean condition, ASTNode node, String message) throws TypeCheckException {
		if (!condition) {
			throw new TypeCheckException(message, node.getSourceLoc());
		}
	}

	// The type of a BooleanLitExpr is always BOOLEAN.
	// Set the type in AST Node for later passes (code generation)
	// Return the type for convenience in this visitor.
	@Override
	public Object visitBooleanLitExpr(BooleanLitExpr booleanLitExpr, Object arg) throws Exception {
		booleanLitExpr.setType(Type.BOOLEAN);
		return Type.BOOLEAN;
	}

	@Override
	public Object visitStringLitExpr(StringLitExpr stringLitExpr, Object arg) throws Exception {
		stringLitExpr.setType(Type.STRING);
		return Type.STRING;
		// throw new UnsupportedOperationException("Unimplemented visit method.");
	}

	@Override
	public Object visitIntLitExpr(IntLitExpr intLitExpr, Object arg) throws Exception {
		intLitExpr.setType(Type.INT);
		return Type.INT;
		// throw new UnsupportedOperationException("Unimplemented visit method.");
	}

	@Override
	public Object visitFloatLitExpr(FloatLitExpr floatLitExpr, Object arg) throws Exception {
		floatLitExpr.setType(Type.FLOAT);
		return Type.FLOAT;
	}

	@Override
	public Object visitColorConstExpr(ColorConstExpr colorConstExpr, Object arg) throws Exception {
		colorConstExpr.setType(Type.COLOR);
		return Type.COLOR;
		// throw new UnsupportedOperationException("Unimplemented visit method.");
	}

	@Override
	public Object visitConsoleExpr(ConsoleExpr consoleExpr, Object arg) throws Exception {
		consoleExpr.setType(Type.CONSOLE);
		return Type.CONSOLE;
	}

	// Visits the child expressions to get their type (and ensure they are correctly
	// typed)
	// then checks the given conditions.
	@Override
	public Object visitColorExpr(ColorExpr colorExpr, Object arg) throws Exception {
		Type redType = (Type) colorExpr.getRed().visit(this, arg);
		Type greenType = (Type) colorExpr.getGreen().visit(this, arg);
		Type blueType = (Type) colorExpr.getBlue().visit(this, arg);
		check(redType == greenType && redType == blueType, colorExpr, "color components must have same type");
		check(redType == Type.INT || redType == Type.FLOAT, colorExpr, "color component type must be int or float");
		Type exprType = (redType == Type.INT) ? Type.COLOR : Type.COLORFLOAT;
		colorExpr.setType(exprType);
		return exprType;
	}

	// Maps forms a lookup table that maps an operator expression pair into result
	// type.
	// This more convenient than a long chain of if-else statements.
	// Given combinations are legal; if the operator expression pair is not in the
	// map, it is an error.
	Map<Pair<Kind, Type>, Type> unaryExprs = Map.of(
			new Pair<Kind, Type>(Kind.BANG, BOOLEAN), BOOLEAN,
			new Pair<Kind, Type>(Kind.MINUS, FLOAT), FLOAT,
			new Pair<Kind, Type>(Kind.MINUS, INT), INT,
			new Pair<Kind, Type>(Kind.COLOR_OP, INT), INT,
			new Pair<Kind, Type>(Kind.COLOR_OP, COLOR), INT,
			new Pair<Kind, Type>(Kind.COLOR_OP, IMAGE), IMAGE,
			new Pair<Kind, Type>(Kind.IMAGE_OP, IMAGE), INT);

	// Visits the child expression to get the type, then uses the above table to
	// determine the result type
	// and check that this node represents a legal combination of operator and
	// expression type.
	@Override
	public Object visitUnaryExpr(UnaryExpr unaryExpr, Object arg) throws Exception {
		// !, -, getRed, getGreen, getBlue
		Kind op = unaryExpr.getOp().getKind();
		Type exprType = (Type) unaryExpr.getExpr().visit(this, arg);
		// Use the lookup table above to both check for a legal combination of operator
		// and expression, and to get result type.
		Type resultType = unaryExprs.get(new Pair<Kind, Type>(op, exprType));
		check(resultType != null, unaryExpr, "incompatible types for unaryExpr");
		unaryExpr.setType(resultType);
		return resultType;
	}

	// This method has several cases. Work incrementally and test as you go.
	@Override
	public Object visitBinaryExpr(BinaryExpr binaryExpr, Object arg) throws Exception {
		// TODO: implement this method
		// throw new UnsupportedOperationException("Unimplemented visit method.");
		Kind op = binaryExpr.getOp().getKind();
		Type leftType = (Type) binaryExpr.getLeft().visit(this, arg);
		Type rightType = (Type) binaryExpr.getRight().visit(this, arg);
		Type resultType = null;
		switch (op) {// AND, OR, PLUS, MINUS, TIMES, DIV, MOD, EQUALS, NOT_EQUALS, LT, LE, GT,GE
			case AND, OR -> {
				if (leftType == BOOLEAN && rightType == BOOLEAN) {
					resultType = BOOLEAN;
				} else {
					check(false, binaryExpr, "incompatible types for AND-OR");
				}
			}
			case EQUALS, NOT_EQUALS -> {
				check(leftType == rightType, binaryExpr, "incompatible types for comparison");
				resultType = Type.BOOLEAN;
			}
			case PLUS, MINUS -> {
				if (leftType == Type.INT && rightType == Type.INT)
					resultType = Type.INT;
				else if (leftType == Type.FLOAT && rightType == Type.FLOAT)
					resultType = Type.FLOAT;
				else if (leftType == Type.INT && rightType == Type.FLOAT) {

					resultType = Type.FLOAT;
				} else if (leftType == Type.FLOAT && rightType == Type.INT)
					resultType = Type.FLOAT;
				else if (leftType == Type.COLOR && rightType == Type.COLOR)
					resultType = Type.COLOR;
				else if (leftType == Type.COLORFLOAT && rightType == Type.COLORFLOAT)
					resultType = Type.COLORFLOAT;
				else if (leftType == Type.COLORFLOAT && rightType == Type.COLOR)
					resultType = Type.COLORFLOAT;
				else if (leftType == Type.COLOR && rightType == Type.COLORFLOAT)
					resultType = Type.COLORFLOAT;
				else if (leftType == Type.IMAGE && rightType == Type.IMAGE)
					resultType = Type.IMAGE;
				else
					check(false, binaryExpr, "incompatible types for SUMMATION");
			}
			case TIMES, DIV, MOD -> {
				if (leftType == Type.INT && rightType == Type.INT)
					resultType = Type.INT;
				else if (leftType == Type.FLOAT && rightType == Type.FLOAT)
					resultType = Type.FLOAT;
				else if (leftType == Type.INT && rightType == Type.FLOAT)
					resultType = Type.FLOAT;
				else if (leftType == Type.FLOAT && rightType == Type.INT)
					resultType = Type.FLOAT;
				else if (leftType == Type.COLOR && rightType == Type.COLOR)
					resultType = Type.COLOR;
				else if (leftType == Type.COLORFLOAT && rightType == Type.COLORFLOAT)
					resultType = Type.COLORFLOAT;
				else if (leftType == Type.COLORFLOAT && rightType == Type.COLOR)
					resultType = Type.COLORFLOAT;
				else if (leftType == Type.COLOR && rightType == Type.COLORFLOAT)
					resultType = Type.COLORFLOAT;
				else if (leftType == Type.IMAGE && rightType == Type.IMAGE)
					resultType = Type.IMAGE;
				else if (leftType == Type.BOOLEAN && rightType == Type.BOOLEAN)
					resultType = Type.BOOLEAN;
				else if (leftType == Type.IMAGE && rightType == Type.INT)
					resultType = Type.IMAGE;
				else if (leftType == Type.IMAGE && rightType == Type.FLOAT)
					resultType = Type.IMAGE;
				else if (leftType == Type.INT && rightType == Type.COLOR)
					resultType = Type.COLOR;
				else if (leftType == Type.COLOR && rightType == Type.INT)
					resultType = Type.COLOR;
				else if (leftType == Type.FLOAT && rightType == Type.COLOR)
					resultType = Type.COLORFLOAT;
				else if (leftType == Type.COLOR && rightType == Type.FLOAT)
					resultType = Type.COLORFLOAT;
				else
					check(false, binaryExpr, "incompatible types for MUL-DIV-MOD");
			}
			case LT, LE, GT, GE -> {
				if (leftType == Type.INT && rightType == Type.INT)
					resultType = Type.BOOLEAN;
				else if (leftType == Type.FLOAT && rightType == Type.FLOAT)
					resultType = Type.BOOLEAN;
				else if (leftType == Type.INT && rightType == Type.FLOAT)
					resultType = Type.BOOLEAN;
				else if (leftType == Type.FLOAT && rightType == Type.INT)
					resultType = Type.BOOLEAN;
				else
					check(false, binaryExpr, "incompatible types for comparing");
			}
			default -> {
				throw new Exception("compiler error");
			}
		}
		binaryExpr.setType(resultType);
		return resultType;
	}

	@Override
	public Object visitIdentExpr(IdentExpr identExpr, Object arg) throws Exception {
		// TODO: implement this method
		String name = identExpr.getName();
		Declaration dec = symbolTable.lookup(name);
		check(dec != null, identExpr, "undefined identifier " + name);
		check(dec.isAssigned(), identExpr, "using uninitialized variable");
		identExpr.setDec(dec); // save declaration--will be useful later.
		Type type = dec.getType();
		identExpr.setType(type);
		return type;
		// throw new UnsupportedOperationException("Unimplemented visit method.");
	}

	@Override
	public Object visitConditionalExpr(ConditionalExpr conditionalExpr, Object arg) throws Exception {
		Type expType = (Type) conditionalExpr.getCondition().visit(this, arg);
		check(expType == Type.BOOLEAN, conditionalExpr, "Conditional Selector can only be applied to BOOLEAN");
		Type trueType = (Type) conditionalExpr.getTrueCase().visit(this, arg);
		Type flaseType = (Type) conditionalExpr.getTrueCase().visit(this, arg);
		check(trueType == flaseType, conditionalExpr, "True type and false type must be same");
		conditionalExpr.setType(trueType); // IDK IF this is correct will have to check again
		return conditionalExpr.getType();
		// throw new UnsupportedOperationException();
	}

	@Override
	public Object visitDimension(Dimension dimension, Object arg) throws Exception {
		// TODO implement this method
		throw new UnsupportedOperationException();
	}

	@Override
	// This method can only be used to check PixelSelector objects on the right hand
	// side of an assignment.
	// Either modify to pass in context info and add code to handle both cases, or
	// when on left side
	// of assignment, check fields from parent assignment statement.
	public Object visitPixelSelector(PixelSelector pixelSelector, Object arg) throws Exception {
		Type xType = (Type) pixelSelector.getX().visit(this, arg);
		check(xType == Type.INT, pixelSelector.getX(), "only ints as pixel selector components");
		Type yType = (Type) pixelSelector.getY().visit(this, arg);
		check(yType == Type.INT, pixelSelector.getY(), "only ints as pixel selector components");
		return null;
	}

	@Override
	// This method several cases--you don't have to implement them all at once.
	// Work incrementally and systematically, testing as you go.
	public Object visitAssignmentStatement(AssignmentStatement assignmentStatement, Object arg) throws Exception {
		// TODO: implement this method
		Type t1 = (Type) assignmentStatement.getSelector().visit(this, arg);
		Type t2 = (Type) assignmentStatement.getExpr().visit(this, arg);
		check(!t1.equals(t2), assignmentStatement, "Error in visitStatementAssign");

		return null;
		// throw new UnsupportedOperationException("Unimplemented visit method.");
	}

	@Override
	public Object visitWriteStatement(WriteStatement writeStatement, Object arg) throws Exception {
		Type sourceType = (Type) writeStatement.getSource().visit(this, arg);
		Type destType = (Type) writeStatement.getDest().visit(this, arg);
		check(destType != Type.STRING || destType != Type.CONSOLE, writeStatement,
				"illegal destination type for write");
		check(sourceType == Type.CONSOLE, writeStatement, "illegal source type for write");
		return null;
	}

	@Override
	public Object visitReadStatement(ReadStatement readStatement, Object arg) throws Exception {
		Type lhs = (Type) readStatement.getTargetType();
		Type sourceDec = (Type) readStatement.getTargetDec().visit(this, arg);
		check(sourceDec == null, readStatement, "illegal destination type for read");
		check(sourceDec != Type.INT, readStatement, "Error in read Statment");

		return null;
		// throw new UnsupportedOperationException("Unimplemented visit method.");
	}

	@Override
	public Object visitVarDeclaration(VarDeclaration declaration, Object arg) throws Exception {
		// boolean b = symbolTable.insert(declaration.getName(), declaration);
		// chcek( ,declaration, "Error in visitDeclaration");
		// if ((declaration.getDim().getHeight() == null &&
		// declaration.getDim().getWidth() != null)
		// || (declaration.getDim().getWidth() == null &&
		// declaration.getDim().getHeight() != null)) {
		// throw new SyntaxException(declaration, "Error in visitDeclaration");
		// }
		// if (declaration.getDim().getWidth() != null &&
		// declaration.getDim().getHeight() != null) {
		// Type t1 = (Type) declaration.getDim().getWidth().visit(this, arg);
		// Type t2 = (Type) declaration.getDim().getHeight().visit(this, arg);
		// if (t1.equals(Type.INT) && t2.equals(Type.INT) &&
		// declaration.getType().equals(Kind.IMAGE_OP)) {
		// declaration.setType(Types.getType(declaration.type));
		// } else {
		// throw new SyntaxException(declaration, "Error in visitDeclaration");
		// }
		// } else {
		// declaration.setType(Type.getType(declaration));
		// }
		String name = declaration.getName();
		boolean inserted = symbolTable.insert(name, declaration);
		// check(inserted, declaration, "variable " + name + "already declared");
		// Expr initializer = declaration.isInitialized();
		// if (initializer != null) {
		// // infer type of initializer
		// Type initializerType = (Type) initializer.visit(this, arg);
		// check(assignmentCompatible(declaration.getType(), initializerType),
		// declaration,
		// "type of expression and declared type do not match");
		// declaration.setAssigned(true);
		// }
		return null;
		// throw new UnsupportedOperationException("Unimplemented visit method.");
	}

	@Override
	public Object visitProgram(Program program, Object arg) throws Exception {
		// TODO: this method is incomplete, finish it.
		// Save root of AST so return type can be accessed in return statements
		root = program;

		// Check declarations and statements
		List<ASTNode> decsAndStatements = program.getDecsAndStatements();
		for (ASTNode node : decsAndStatements) {
			if (node instanceof Declaration) {
				Declaration d1 = (Declaration) node;
				d1.visit(this, arg);
			} else {
				Statement s1 = (Statement) node;
				s1.visit(this, arg);
			}
		}
		return program;
	}

	@Override
	public Object visitNameDef(NameDef nameDef, Object arg) throws Exception {
		// TODO: implement this method
		throw new UnsupportedOperationException();
	}

	@Override
	public Object visitNameDefWithDim(NameDefWithDim nameDefWithDim, Object arg) throws Exception {
		// TODO: implement this method
		throw new UnsupportedOperationException();
	}

	@Override
	public Object visitReturnStatement(ReturnStatement returnStatement, Object arg) throws Exception {
		Type returnType = root.getReturnType(); // This is why we save program in visitProgram.
		Type expressionType = (Type) returnStatement.getExpr().visit(this, arg);
		check(returnType == expressionType, returnStatement, "return statement with invalid type");
		return null;
	}

	@Override
	public Object visitUnaryExprPostfix(UnaryExprPostfix unaryExprPostfix, Object arg) throws Exception {
		Type expType = (Type) unaryExprPostfix.getExpr().visit(this, arg);
		check(expType == Type.IMAGE, unaryExprPostfix, "pixel selector can only be applied to image");
		unaryExprPostfix.getSelector().visit(this, arg);
		unaryExprPostfix.setType(Type.INT);
		unaryExprPostfix.setCoerceTo(COLOR);
		return Type.COLOR;
	}

}
