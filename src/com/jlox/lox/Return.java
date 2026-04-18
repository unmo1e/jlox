package com.jlox.lox;

class Return extends RuntimeException {
    final Object value;

    Return(Object value) {
        // this disables some java exception features
        // improves performace (hopefully)
        super(null, null, false, false);
        this.value = value;
    }
}
