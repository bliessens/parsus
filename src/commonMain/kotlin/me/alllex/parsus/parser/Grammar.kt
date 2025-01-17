package me.alllex.parsus.parser

import me.alllex.parsus.token.EofToken
import me.alllex.parsus.token.Token
import kotlin.reflect.KProperty

/**
 * Marker interface to scope extensions.
 */
interface GrammarContext

/**
 * Grammar defines all tokens that are expected to occur in the input
 * and the [root] parser which is used to [parse] the resulting value.
 *
 * The tokens must either be [registered][register] manually or using a property-delegate with the `by` keyword.
 * Declaring parsers using `by` is also a preferred way as it allows to use them recursively.
 *
 * ```kotlin
 * // Grammar that parses sums of numbers: 1 + 4 + 2
 * object G : Grammar<Int>() {
 *     init { regexToken("\\s+", ignored = true) }
 *     val lpar by literalToken("(")
 *     val rpar by literalToken(")")
 *     val plus by literalToken("+")
 *     val int by regexToken("\\d+")
 *
 *     val number by parser { int() } map { it.text.toInt() }
 *     val braced by parser { skip(lpar) * root() * skip(rpar) }
 *     val term by number or braced
 *
 *     override val root: Parser<Int> by parser {
 *         leftAssociative(term, plus) { l, _, r -> l + r }
 *     }
 * }
 * ```
 */
abstract class Grammar<out V>(
    val ignoreCase: Boolean = false,
    private val debugMode: Boolean = false,
) : GrammarContext {

    private val _tokens = mutableListOf<Token>()
    private var freezeTokens = false

    init {
        // important that it is the first
        register(EofToken)
    }

    abstract val root: Parser<V>

    /**
     * Parses entire input with the [root] parser of the grammar
     * and returns the parsed value wrapped in the [ParseResult].
     * If parsing fails the result will be a [ParseError].
     */
    fun parse(input: String): ParseResult<V> {
        return parseEntire(root, input)
    }

    /**
     * Parses entire input with the [root] parser of the grammar
     * and returns the parsed value wrapped in the [ParseResult].
     * If parsing fails throws a [ParseException] containing an error.
     */
    @Throws(ParseException::class)
    fun parseOrThrow(input: String): V {
        return parse(input).getOrThrow()
    }

    /**
     * Parses entire input with the provided [parser] (instead of the [root] of the grammar)
     * and returns the parsed value wrapped in the [ParseResult].
     * If parsing fails the result will be a [ParseError].
     */
    fun <T> parse(parser: Parser<T>, input: String): ParseResult<T> {
        return parseEntire(parser, input)
    }

    /**
     * Parses entire input with the provided [parser] (instead of the [root] of the grammar)
     * and returns the parsed value wrapped in the [ParseResult].
     * If parsing fails throws a [ParseException] containing an error.
     */
    @Throws(ParseException::class)
    fun <T> parseOrThrow(parser: Parser<T>, input: String): T {
        return parse(parser, input).getOrThrow()
    }

    /**
     * Parses entire input with the [root] parser of the grammar
     * and returns the parsed value wrapped in the [ParseResult].
     * If parsing fails the result will be a [ParseError].
     */
    @Deprecated("Use parse instead", ReplaceWith("parse(input)"))
    fun parseEntire(input: String): ParseResult<V> {
        return parse(input)
    }

    /**
     * Parses entire input with the [root] parser of the grammar
     * and returns the parsed value wrapped in the [ParseResult].
     * If parsing fails throws a [ParseException] containing an error.
     */
    @Deprecated("Use parseOrThrow instead", ReplaceWith("parseOrThrow(input)"))
    @Throws(ParseException::class)
    fun parseEntireOrThrow(input: String): V {
        return parseOrThrow(input)
    }

    override fun toString(): String {
        return "Grammar(${_tokens.size} tokens, root = $root)"
    }

    /**
     * Registers a token in the grammar.
     *
     * Tokens must be registered before parsing.
     * Either register them as property delegates or in the init blocks.
     */
    fun register(token: Token) {
        check(!freezeTokens) { "Tokens must be registered before parsing" }
        check(token !in _tokens) { "Token $token is already registered" }

        _tokens += token
    }

    private fun checkRegistered(token: Token) {
        check(token in _tokens) { "Token $token is not registered" }
    }

    protected operator fun <T : Token> T.provideDelegate(thisRef: Grammar<*>, property: KProperty<*>): T =
        also {
            if (it.name == null) {
                it.name = property.name
            }
            checkRegistered(it)
        }

    protected operator fun <T : Token> T.getValue(thisRef: Grammar<*>, property: KProperty<*>): T = this

    protected operator fun <R> Parser<R>.provideDelegate(thisRef: Grammar<*>, property: KProperty<*>): Parser<R> =
        also {
            if (it is ParserImpl && it.name == null) {
                it.name = property.name
            }
        }

    protected operator fun <R> Parser<R>.getValue(thisRef: Grammar<*>, property: KProperty<*>): Parser<R> = this

    private fun <T> parseEntire(parser: Parser<T>, input: String): ParseResult<T> {
        freezeTokens = true
        val lexer = Lexer(input, _tokens)
        val parsingContext = ParsingContext(lexer, debugMode)
        val untilEofParser = parser {
            val r = parser()
            EofToken()
            r
        }

        return parsingContext.runParser(untilEofParser)
    }
}

/**
 * Attempts to parse the entire input and returns the parsed value or the default value if parsing fails.
 */
inline fun <V> Grammar<V>.parseOrElse(input: String, default: (ParseError) -> V): V {
    return when (val result = parse(input)) {
        is ParsedValue -> result.value
        is ParseError -> default(result)
    }
}

/**
 * Attempts to parse the entire input and returns the parsed value or `null` if parsing fails.
 */
fun <V> Grammar<V>.parseOrNull(input: String): V? = parseOrElse(input) { null }
