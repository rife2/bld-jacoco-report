package com.example;

;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExamplesTest {
    @Test
    void verifyHello() {
        assertEquals("Hello World!", new Examples().getMessage());
    }

    public static void main(String[] args) {
        new ExamplesTest().verifyHello();
    }
}