package org.example

import java.io.File

data class Word(
    val original: String,
    val translate: String,
    val correctAnswersCount: Int = 0
)

fun main() {

    val wordsFile: File = File("words.txt")
    val dictionary = mutableListOf<Word>()

    wordsFile.forEachLine { line ->
        val parts = line.split("|")
        val word = Word(original = parts[0], translate = parts[1], correctAnswersCount = parts[2].toIntOrNull() ?: 0)
        dictionary.add(word)
    }
dictionary.forEach { println(it) }
}