package com.architectai.core.domain.dsl

import org.junit.Test
import org.junit.Assert.*
import kotlin.system.measureTimeMillis

class MagnaPyPerformanceTest {

    @Test
    fun `dsl evaluation of 100 tiles completes under 5s`() {
        // Grid is 0..29, squares at spacing of 3 => positions 0,3,6,...,27
        // 10 cols x 10 rows = 100 tiles, all within 0..27
        val script = """
            // Design: Performance Test
            group {
                repeat(10) { row ->
                    group {
                        repeat(10) { col ->
                            square(color = RED).at(col * 3, row * 3)
                        }
                    }
                }
            }
        """.trimIndent()

        val time = measureTimeMillis {
            val tokens = MagnaPyLexer(script).tokenize()
            val ast = MagnaPyParser(tokens).parse().getOrThrow()
            val tiles = MagnaPyEvaluator().evaluate(ast).getOrThrow()
            assertEquals("Should produce 100 tiles", 100, tiles.size)
        }

        println("DSL 100-tile evaluation: ${time}ms")
        assertTrue("DSL evaluation should be under 5s, was ${time}ms", time < 5000)
    }

    @Test
    fun `dsl lexer tokenizes large script under 1s`() {
        // Build a large script
        val lines = (1..200).map { i ->
            "square(color = RED).at(${(i * 3) % 30}, ${(i / 10) * 3})"
        }
        val script = "// Design: Big\n" + lines.joinToString("\n")

        val time = measureTimeMillis {
            MagnaPyLexer(script).tokenize()
        }

        println("Lexer 200-line script: ${time}ms")
        assertTrue("Lexer should be under 1s, was ${time}ms", time < 1000)
    }

    @Test
    fun `dsl parser parses 200-tile script under 1s`() {
        val lines = (1..200).map { i ->
            "square(color = RED).at(${(i * 3) % 30}, ${(i / 10) * 3})"
        }
        val script = "// Design: Big\n" + lines.joinToString("\n")

        val tokens = MagnaPyLexer(script).tokenize()
        val time = measureTimeMillis {
            MagnaPyParser(tokens).parse().getOrThrow()
        }

        println("Parser 200-tile script: ${time}ms")
        assertTrue("Parser should be under 1s, was ${time}ms", time < 1000)
    }
}
