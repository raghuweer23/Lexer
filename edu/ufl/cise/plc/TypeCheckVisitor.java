package edu.ufl.cise.plc;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
		return STRING;
	}

	@Override
	public Object visitIntLitExpr(IntLitExpr intLitExpr, Object arg) throws Exception {
		intLitExpr.setType(Type.INT);
		return INT;
	}

	@Override
	public Object visitFloatLitExpr(FloatLitExpr floatLitExpr, Object arg) throws Exception {
		floatLitExpr.setType(Type.FLOAT);
		return Type.FLOAT;
	}

	@Override
	public Object visitColorConstExpr(ColorConstExpr colorConstExpr, Object arg) throws Exception {
		colorConstExpr.setType(Type.COLOR);
		return COLOR;
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
		// Save the type of the unary expression in the AST node for use in code
		// generation later.
		unaryExpr.setType(resultType);
		// return the type for convenience in this visitor.
		return resultType;
	}

	// This method has several cases. Work incrementally and test as you go.
	@Override
	public Object visitBinaryExpr(BinaryExpr binaryExpr, Object arg) throws Exception {
		Kind op = binaryExpr.getOp().getKind();
		Type lType = (Type) binaryExpr.getLeft().visit(this, arg);
		Type rType = (Type) binaryExpr.getRight().visit(this, arg);

		Type resultType = null;
		switch (op) { // EQUALS, NOT_EQUALS, PLUS, MINUS, TIMES, DIV, MOD, LT, LE, GT, GE
			case AND, OR -> {
				check(lType == Type.BOOLEAN && rType == Type.BOOLEAN, binaryExpr, "Booleans required");
				resultType = Type.BOOLEAN;
			}
			case EQUALS, NOT_EQUALS -> {
				check(lType == rType, binaryExpr, "Incompatible types for comparison");
				resultType = Type.BOOLEAN;
			}
			case PLUS, MINUS -> {
				if (lType == Type.INT && rType == Type.INT) {
					resultType = Type.INT;
				} else if (lType == Type.FLOAT && rType == Type.FLOAT) {
					resultType = Type.FLOAT;
				} else if (lType == Type.FLOAT && rType == Type.INT) {
					// Coerce to float
					binaryExpr.getRight().setCoerceTo(Type.FLOAT);
					resultType = Type.FLOAT;
				} else if (lType == Type.INT && rType == Type.FLOAT) {
					// Coerce to float
					binaryExpr.getLeft().setCoerceTo(Type.FLOAT);
					resultType = Type.FLOAT;
				} else if (lType == Type.COLOR && rType == Type.COLOR) {
					resultType = Type.COLOR;
				} else if (lType == Type.COLORFLOAT && rType == Type.COLORFLOAT) {
					resultType = Type.COLORFLOAT;
				} else if (lType == Type.COLORFLOAT && rType == Type.COLOR) {
					// Coerce to colorfloat
					binaryExpr.getRight().setCoerceTo(Type.COLORFLOAT);
					resultType = Type.COLORFLOAT;
				} else if (lType == Type.COLOR && rType == Type.COLORFLOAT) {
					// Coerce to colorfloat
					binaryExpr.getLeft().setCoerceTo(Type.COLORFLOAT);
					resultType = Type.COLORFLOAT;
				} else if (lType == Type.IMAGE && rType == Type.IMAGE) {
					resultType = Type.IMAGE;
				} else
					check(false, binaryExpr, "incompatible types for operator");
			}
			case TIMES, DIV, MOD -> {
				if (lType == Type.IMAGE && rType == Type.INT) {
					resultType = Type.IMAGE;
				} else if (lType == Type.IMAGE && rType == Type.FLOAT) {
					resultType = Type.IMAGE;
				} else if (lType == Type.INT && rType == Type.COLOR) {
					binaryExpr.getLeft().setCoerceTo(Type.COLOR);
					resultType = Type.COLOR;
				} else if (lType == Type.COLOR && rType == Type.INT) {
					binaryExpr.getRight().setCoerceTo(Type.COLOR);
					resultType = Type.COLOR;
				} else if (lType == Type.FLOAT && rType == Type.COLOR) {
					binaryExpr.getLeft().setCoerceTo(Type.COLORFLOAT);
					binaryExpr.getRight().setCoerceTo(Type.COLORFLOAT);
					resultType = Type.COLORFLOAT;
				} else
					check(false, binaryExpr, "incompatible types for operator");
			}
			case LT, LE, GT, GE -> {
				if (lType == Type.INT && rType == Type.INT) {
					resultType = Type.BOOLEAN;
				} else if (lType == Type.FLOAT && rType == Type.FLOAT) {
					resultType = Type.BOOLEAN;
				} else if (lType == Type.INT && rType == Type.FLOAT) {
					binaryExpr.getLeft().setCoerceTo(Type.FLOAT);
					resultType = Type.BOOLEAN;
				} else if (lType == Type.FLOAT && rType == Type.INT) {
					binaryExpr.getRight().setCoerceTo(Type.FLOAT);
					resultType = Type.BOOLEAN;
				} else
					check(false, binaryExpr, "incompatible types for operator");
			}
			default -> check(false, binaryExpr, "use a real operator");
		}
		binaryExpr.setType(resultType);
		return resultType;
	}

	@Override
	public Object visitIdentExpr(IdentExpr identExpr, Object arg) throws Exception {
		String name = identExpr.getText();
		Declaration dec = symbolTable.lookup(name);
		check(dec != null, identExpr, "Undefined identifier " + name);
		check(dec.isInitialized(), identExpr, "Uninitialized identifier used: " + name);

		identExpr.setDec(dec); // Useful later, apparently

		Type type = dec.getType();
		identExpr.setType(type);
		return type;
	}

	@Override
	public Object visitConditionalExpr(ConditionalExpr conditionalExpr, Object arg) throws Exception {
		Type conditionCase = (Type) symbolTable.lookup(conditionalExpr.getCondition().getText()).visit(this, arg);
		Type trueCase = (Type) symbolTable.lookup(conditionalExpr.getTrueCase().getText()).visit(this, arg);
		Type falseCase = (Type) symbolTable.lookup(conditionalExpr.getFalseCase().getText()).visit(this, arg);

		check(conditionCase == Type.BOOLEAN, conditionalExpr, "Condition case must be boolean");
		check(trueCase == falseCase, conditionalExpr, "True case must equal false case");

		conditionalExpr.setType(trueCase);
		return trueCase;
	}

	@Override
	public Object visitDimension(Dimension dimension, Object arg) throws Exception {
		Type expr1Type = (Type) dimension.getWidth().visit(this, arg);
		Type expr2Type = (Type) dimension.getHeight().visit(this, arg);

		check(expr1Type == Type.INT, dimension.getWidth(), "Width is not of type int!");
		check(expr2Type == Type.INT, dimension.getHeight(), "Height is not of type int!");
		return null;
	}

	@Override
	// This method can only be used to check PixelSelector objects on the right hand
	// side of an assignment.
	// Either modify to pass in context info and insert code to handle both cases,
	// or
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
		Declaration targetDec = symbolTable.lookup(assignmentStatement.getName());
		check(targetDec != null, assignmentStatement, "variable is undeclared: " + assignmentStatement.getName());
		Type targetType = targetDec.getType();
		Type exprType = (Type) assignmentStatement.getExpr().visit(this, arg);
		assignmentStatement.setTargetDec(targetDec);
		boolean compatible = false;
		if (targetType != IMAGE) {
			check(assignmentStatement.getSelector() == null, assignmentStatement, "non image can't have" +
					"pixel selector");
			if (targetType == exprType)
				compatible = true;
			else if ((targetType == INT && exprType == FLOAT) || (targetType == FLOAT && exprType == INT) ||
					(targetType == INT && exprType == COLOR) || (targetType == COLOR && exprType == INT)) {
				assignmentStatement.getExpr().setCoerceTo(targetType);
				compatible = true;
			}
		} else {
			if (assignmentStatement.getSelector() == null) {
				if (exprType == COLOR || exprType == COLORFLOAT || exprType == INT || exprType == FLOAT) {
					compatible = true;
					if (exprType == INT)
						assignmentStatement.getExpr().setCoerceTo(COLOR);
					else if (exprType == FLOAT)
						assignmentStatement.getExpr().setCoerceTo(COLORFLOAT);
				}
			} else {
				String nameX = assignmentStatement.getSelector().getX().getText();
				String nameY = assignmentStatement.getSelector().getY().getText();
				check(symbolTable.lookup(nameX) == null && symbolTable.lookup(nameY) == null,
						assignmentStatement,
						"variables in pixel selector cannot be global variables");
				assignmentStatement.getSelector().getX().setType(INT);
				assignmentStatement.getSelector().getY().setType(INT);
				check(assignmentStatement.getSelector().getX() instanceof IdentExpr &&
						assignmentStatement.getSelector().getY() instanceof IdentExpr, assignmentStatement,
						"Pixel selector on left side of assignment has non ident");
				if (exprType == INT || exprType == COLOR || exprType == COLORFLOAT || exprType == FLOAT) {
					compatible = true;
					if (exprType == INT || exprType == COLORFLOAT || exprType == FLOAT) {
						assignmentStatement.getExpr().setCoerceTo(COLOR);
					}
				}
			}

		}
		check(compatible, assignmentStatement, "incompatible types in assignment of variable: "
				+ assignmentStatement.getName());
		targetDec.setInitialized(true);
		return null;
	}

	@Override
	public Object visitWriteStatement(WriteStatement writeStatement, Object arg) throws Exception {
		Type sourceType = (Type) writeStatement.getSource().visit(this, arg);
		Type destType = (Type) writeStatement.getDest().visit(this, arg);
		check(destType == Type.STRING || destType == Type.CONSOLE, writeStatement,
				"illegal destination type for write");
		check(sourceType != Type.CONSOLE, writeStatement, "illegal source type for write");
		return null;
	}

	@Override
	public Object visitReadStatement(ReadStatement readStatement, Object arg) throws Exception {
		Type targetType = (Type) symbolTable.lookup(readStatement.getName()).visit(this, arg);
		check(targetType != null, readStatement.getTargetDec(), "Target not declared!");
		check(readStatement.getSelector() == null, readStatement.getSelector(), "Cannot have a pixel selector!");

		Type exprType = (Type) readStatement.getSource().visit(this, arg);

		check(exprType == Type.CONSOLE || exprType == Type.STRING, readStatement.getSource(),
				"RHS must be of type console or string!");

		readStatement.getTargetDec().isInitialized();

		return null;
	}

	@Override
	public Object visitVarDeclaration(VarDeclaration declaration, Object arg) throws Exception {
		Type type = (Type) declaration.getNameDef().visit(this, arg);
		Type exprType = null;
		if (declaration.getOp() != null) {
			exprType = (Type) declaration.getExpr().visit(this, arg);
		}
		boolean compatible = false;
		if (type == IMAGE) {
			check(exprType == IMAGE || declaration.getNameDef().getDim() != null, declaration, "Image must be" +
					"assigned either an Image or have a Dimension");
			compatible = true;
		} else if (declaration.getOp() == null) {
			return null;
		} else if (declaration.getOp().getKind() == Kind.ASSIGN) {
			if (type == exprType)
				compatible = true;
			else if ((type == INT && exprType == FLOAT) || (type == FLOAT && exprType == INT) ||
					(type == INT && exprType == COLOR) || (type == COLOR && exprType == INT)) {
				declaration.getExpr().setCoerceTo(type);
				compatible = true;
			}
		} else if (declaration.getOp().getKind() == Kind.LARROW) {
			check(exprType == CONSOLE || exprType == STRING, declaration, "Right side must be CONSOLE or STRING");
		}
		declaration.getNameDef().setInitialized(true);
		return null;
	}

	@Override
	public Object visitProgram(Program program, Object arg) throws Exception {
		// Save root of AST so return type can be accessed in return statements
		root = program;

		List<NameDef> params = program.getParams();
		for (NameDef node : params) {
			node.visit(this, arg);
			node.setInitialized(true);
		}
		// Check declarations and statements
		List<ASTNode> decsAndStatements = program.getDecsAndStatements();
		for (ASTNode node : decsAndStatements) {
			node.visit(this, arg);
		}
		return program;
	}

	@Override
	public Object visitNameDef(NameDef nameDef, Object arg) throws Exception {
		String name = nameDef.getName();
		boolean inserted = symbolTable.insert(name, nameDef);
		check(inserted, nameDef, "variable: " + name + " already inserted");
		return nameDef.getType();
	}

	@Override
	public Object visitNameDefWithDim(NameDefWithDim nameDefWithDim, Object arg) throws Exception {
		String name = nameDefWithDim.getName();
		boolean inserted = symbolTable.insert(name, nameDefWithDim);
		check(inserted, nameDefWithDim, "variable: " + name + " already inserted");
		nameDefWithDim.getDim().visit(this, arg);
		return nameDefWithDim.getType();
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