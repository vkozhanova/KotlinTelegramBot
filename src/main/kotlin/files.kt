import java.io.File

data class Word(
    val original: String,
    val translate: String,
    val correctAnswersCount: Int = 0
)

fun main() {

    val dictionary = loadDictionary()

    while (true) {
        println("Меню:")
        println("1 - Учить слова")
        println("2 - Статистика")
        println("0 - Выход")

        val userChoice = readln()

        when (userChoice) {
            "1" -> println("Учить слова")
            "2" -> println("Статистика")
            "0" -> {
                println("Выход")
                break
            }

            else -> {
                println("Некорректный ввод. Введите число 1, 2 или 0")
                continue
            }
        }
    }
}

fun loadDictionary(): MutableList<Word> {
    val wordsFile: File = File("words.txt")
    val dictionary = mutableListOf<Word>()

    wordsFile.forEachLine { line ->
        val parts = line.split("|")
        val word = Word(
            original = parts[0],
            translate = parts[1],
            correctAnswersCount = parts.getOrNull(2)?.toIntOrNull() ?: 0
        )
        dictionary.add(word)
    }
    return dictionary
}
