package com.jlox.lox;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import static com.jlox.lox.TokenType.*;

class Parser {
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> _tokens) {
        tokens = _tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    private Stmt declaration() {
        try {
            if(match(FUN)) return function("function");
            if(match(VAR)) return varDeclaration();
            return statement();
        } catch(ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt.Function function(String kind) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();
        if(!check(RIGHT_PAREN)) {
            do {
                if(parameters.size() >= 255)
                    error(peek(), "Can't have more than 255 parameters.");

                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while(match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();

        return new Stmt.Function(name, parameters, body);
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expected a variable name.");
        // the optional initializer
        Expr initializer = null;
        if(match(EQUAL)) {
            initializer = expression();
        }
        consume(SEMICOLON, "Expected a ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(WHILE)) return whileStatement();
        if (match(FOR)) return forStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        if (match(RETURN)) return returnStatement();

        return expressionStatement();
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
        while(!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }
        consume(RIGHT_BRACE, "Expect '}' after a block.");
        return statements;
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after while.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition expression.");
        Stmt body = statement();
        return new Stmt.While(condition, body);
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after for.");

        // initializer
        Stmt initializer = null;
        if(match(SEMICOLON)) {
            initializer = null;
        } else if(match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        // condition (we see if token is semicolon
        // to determine if we have a condition)
        Expr condition = null;
        if(!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after for loop condition");

        // increment condition (see if token is right paren)
        Expr increment = null;
        if(!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for loop update expression.");

        // body
        Stmt body = statement();

        // convert to while loop
        // 1. add increment to the body using Block
        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body,
                                                new Stmt.Expression(increment)));
        }

        // 2. make the loop
        if(condition == null)
            condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        // 3. add initializer using Block
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        // return the for loop converted to while loop in Parse Tree
        return body;
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }
        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after if.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if(match(ELSE))
            elseBranch = statement();

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private Expr expression() {
        return assignment();
    }

    // assignment
    private Expr assignment() {
        Expr expr = or();

        // if we have a equal sign, it is assignment
        if(match(EQUAL)) {
            Token equals = previous();
            // since assignment is right-associative
            // we call the function itself rather than equality();
            Expr value = assignment();

            // we need to check if expr is proper l-value
            if(expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }
        return expr;
    }

    // logical or and and
    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    // == and !=
    private Expr equality() {
        Expr expr = comparision();
        while(match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparision();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    // >, >=, < and <=
    private Expr comparision() {
        Expr expr = term();
        while(match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    // + and -
    private Expr term() {
        Expr expr = factor();
        while(match(PLUS, MINUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    // * and /
    private Expr factor() {
        Expr expr = unary();
        while(match(STAR, SLASH)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    // unary - and !
    private Expr unary() {
        if(match(MINUS, BANG)) {
            Token operator = previous();
            Expr expr = unary();
            return new Expr.Unary(operator, expr);
        }
        return call();
    }

    // function call
    private Expr call() {
        Expr expr = primary();

        while(true) {
            if(match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                // we follow a 255 arguments limit
                if(arguments.size() >= 255) {
                    error(peek(), "Can't have more than 255 arguments.");
                }

                arguments.add(expression());
            } while (match(COMMA));
        }
        Token paren = consume(RIGHT_PAREN,"Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
    }

    // literals true, false, Strings, numbers and nil
    // also handle grouping ( expr )
    private Expr primary() {
        if(match(TRUE)) return new Expr.Literal(true);
        if(match(FALSE)) return new Expr.Literal(false);
        if(match(NIL)) return new Expr.Literal(null);

        if(match(STRING, NUMBER)) {
            return new Expr.Literal(previous().literal);
        }

        if(match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expected a ')' after the expression");
            return new Expr.Grouping(expr);
        }

        if(match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        // we got a token that can't make expression
        throw error(peek(), "Expect expression.");
    }

    // helpers
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token consume(TokenType type, String message) {
        // consume the expected token
        if(check(type))
            return advance();
        // throw error
        throw error(peek(), message);
    }

    // error handling
    private static class ParseError extends RuntimeException {}

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            // if we have seen semicolon, a new statement is about to start
            if (previous().type == SEMICOLON) return;
            // tokens which indicate start of new statement
            switch (peek().type) {
            case CLASS:
            case FUN:
            case VAR:
            case FOR:
            case IF:
            case WHILE:
            case PRINT:
            case RETURN:
                return;
            }

            advance();
        }
    }
}
