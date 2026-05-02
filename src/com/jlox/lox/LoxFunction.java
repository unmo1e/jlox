package com.jlox.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Environment closure;
    private final boolean isInitializer;

    LoxFunction(Stmt.Function _declaration, Environment _closure, boolean _isInitializer) {
        declaration = _declaration;
        closure = _closure;
        isInitializer = _isInitializer;
    }

    LoxFunction bind(LoxInstance instance) {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        return new LoxFunction(declaration, environment, isInitializer);
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        // we use closure as the parent environment
        Environment environment = new Environment(closure);
        for(int i = 0; i < declaration.params.size(); i++) {
            environment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }
        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch(Return returnValue) {
            if(isInitializer)
                return closure.getAt(0, "this");
            
            return returnValue.value;
        }

        // initializer will always return object itself
        if(isInitializer)
            return closure.getAt(0, "this");
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
