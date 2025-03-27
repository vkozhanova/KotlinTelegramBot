import java.io.File
import java.io.FileNotFoundException
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement

interface IUserDictionary {
    fun addUserIfNotExist(chatId: Long)
    fun getNumOfLearnedWords(): Int
    fun getSize(): Int
    fun getLearnedWords(): List<Word>
    fun getUnlearnedWords(): List<Word>
    fun setCorrectAnswersCount(word: String, correctAnswersCount: Int)
    fun resetUserProgress()
}

class DatabaseUserDictionary(
    private val connection: Connection,
    private val learningThreshold: Int = 3,

    ) : IUserDictionary {

    private var currentChatId: Long? = null

    fun setCurrentChatId(chatId: Long) {
        this.currentChatId = chatId
    }

    init {
        createTableIfNotExists(connection)
    }

    private fun createTableIfNotExists(connection: Connection) {
        connection.autoCommit = false
        try {
            val createUserTable = """
                    CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username VARCHAR (100) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    chat_id INTEGER UNIQUE NOT NULL
                    )
                    """.trimIndent()
            connection.prepareStatement(createUserTable).use { it.executeUpdate() }

            val createAnswersTable = """
                    CREATE TABLE IF NOT EXISTS user_answers (
                    user_id INTEGER,
                    word_id INTEGER,
                    correct_answer_count INTEGER DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (user_id, word_id),
                    FOREIGN KEY (user_id) REFERENCES users(id),
                    FOREIGN KEY (word_id) REFERENCES words(id)
                    )
                """.trimIndent()
            connection.prepareStatement(createAnswersTable).use { it.executeUpdate() }

            connection.commit()
            println("Транзакция успешно завершена.")
        } catch (e: Exception) {
            connection.rollback()
            println("Ошибка: ${e.message}. Транзакция откачена.")
        } finally {
            connection.autoCommit = true
        }
    }

    override fun addUserIfNotExist(chatId: Long) {
        var attempts = 0
        while (attempts < 5) {
            try {
                connection.prepareStatement(
                    """
             INSERT OR IGNORE INTO users (chat_id) VALUES (?);
         """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, chatId)
                    statement.executeUpdate()
                }
                return
            } catch (e: SQLException) {
                if (e.message?.contains("SQLITE_BUSY") == true) {
                    attempts++
                    Thread.sleep(100)
                } else {
                    println("Ошибка при добавлении пользователя: ${e.message}")
                    throw e
                }
            }
        }
        throw RuntimeException("Не удалось добавить пользователя после нескольких попыток")
    }

    fun deleteWordsAndRelatedDataByIdRange(startId: Int, endId: Int) {
        connection.autoCommit = false
        try {
            val deleteAnswersQuery = "DELETE FROM user_answers WHERE word_id BETWEEN ? AND ?"
            connection.prepareStatement(deleteAnswersQuery).use { statement ->
                statement.setInt(1, startId)
                statement.setInt(2, endId)
                statement.executeUpdate()
            }

            val deleteWordsQuery = "DELETE FROM words WHERE id BETWEEN ? AND ?"
            connection.prepareStatement(deleteWordsQuery).use { statement ->
                statement.setInt(1, startId)
                statement.setInt(2, endId)
                statement.executeUpdate()
            }

            connection.commit()
            println("Строки с id от $startId до $endId и связанные данные успешно удалены")
        } catch (e: Exception) {
            connection.rollback()
            throw RuntimeException("Ошибка в удалении строк: ${e.message}", e)
        } finally {
            connection.autoCommit = true
        }
    }

    override fun getNumOfLearnedWords(): Int {
        if (currentChatId == null) {
            println("Ошибка: currentChatId не установлен.")
            return 0
        }

        val query = """
        SELECT COUNT(*)
        FROM user_answers
        WHERE user_id = (SELECT id FROM users WHERE chat_id = ?)
        AND correct_answer_count >= $learningThreshold;
    """.trimIndent()

        connection.prepareStatement(query).use { statement ->
            statement.setLong(1, currentChatId!!)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) {
                    return resultSet.getInt(1)
                }
            }
        }
        return 0
    }

    override fun getSize(): Int {
        val query = "SELECT COUNT(*) FROM words"
        connection.prepareStatement(query).use { statement ->
            statement.executeQuery().use { resultSet ->
                return if (resultSet.next()) resultSet.getInt(1) else 0
            }

        }
    }

    override fun getLearnedWords(): List<Word> {
        return getWords(
            """
             SELECT words.text, words.translate, user_answers.correct_answer_count
                         FROM words
                         JOIN user_answers ON words.id = user_answers.word_id
                         WHERE user_answers.correct_answer_count >= ?
         """.trimIndent(),
            learningThreshold
        )
    }

    override fun getUnlearnedWords(): List<Word> {
        return getWords(
            """
             SELECT words.text, words.translate, COALESCE(user_answers.correct_answer_count, 0) AS correct_answer_count
                                      FROM words
                                      LEFT JOIN user_answers ON words.id = user_answers.word_id
                                      WHERE user_answers.correct_answer_count IS NULL OR user_answers.correct_answer_count < ?
        """.trimIndent(),
            learningThreshold
        )
    }

    override fun setCorrectAnswersCount(word: String, correctAnswersCount: Int) {
        val chatId = currentChatId ?: throw IllegalStateException("Chat ID не получен")
        connection.prepareStatement(
            """
            INSERT OR REPLACE INTO user_answers (user_id, word_id, correct_answer_count, updated_at)
            VALUES (
            (SELECT id FROM users WHERE chat_id = ?),
            (SELECT id FROM words WHERE text = ?),
            ?,
            CURRENT_TIMESTAMP
            );
  """.trimIndent()
        ).use { statement ->
            statement.setLong(1, chatId)
            statement.setString(2, word)
            statement.setInt(3, correctAnswersCount)
            statement.executeUpdate()
        }
    }

    private fun getWords(query: String, vararg params: Any): List<Word> {
        val words = mutableListOf<Word>()
        connection.prepareStatement(query).use { statement ->
            params.forEachIndexed { index, value ->
                when (value) {
                    is Int -> statement.setInt(index + 1, value)
                    is Long -> statement.setLong(index + 1, value)
                    is String -> statement.setString(index + 1, value)

                }
            }
            statement.executeQuery().use { resultSet ->
                while (resultSet.next()) {
                    words.add(
                        Word(
                            original = resultSet.getString("text"),
                            translate = resultSet.getString("translate"),
                            correctAnswersCount = resultSet.getInt("correct_answer_count")
                        )
                    )
                }
            }
        }
        return words
    }

    override fun resetUserProgress() {
        val query = "DELETE FROM user_answers"
        connection.prepareStatement(query).use { statement ->
            statement.executeUpdate()
        }
    }
}

fun updateDictionary(wordsFile: File, connection: Connection) {
    fun isValidWord(original: String, translate: String): Boolean {
        val regex = Regex("^[\\p{L} .'-]+\$", RegexOption.IGNORE_CASE)
        return original.matches(regex) &&
                translate.matches(regex) &&
                original.length <= 100 &&
                translate.length <= 100
    }

    synchronized(connection) {
        var insertedCount = 0
        var ignoredCount = 0
        var errorCount = 0
        var totalLines = 0

        val insertStatement = connection.prepareStatement(
            "INSERT OR IGNORE INTO words (text, translate) VALUES (?, ?)"
        )

        connection.autoCommit = false
        try {
            wordsFile.forEachLine { line ->
                totalLines++
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) {
                    println("Пустая строка в строке #$totalLines")
                    return@forEachLine
                }

                val parts = trimmedLine.split("|").map { it.trim() }
                if (parts.size != 2 || !isValidWord(parts[0], parts[1])) {
                    println("Некорректный формат в строке #$totalLines: '$trimmedLine'")
                    errorCount++
                    return@forEachLine
                }

                try {
                    insertStatement.apply {
                        setString(1, parts[0])
                        setString(2, parts[1])
                        addBatch()
                    }
                } catch (e: SQLException) {
                    println("Ошибка при обработке строки #$totalLines: ${e.message}")
                    errorCount++
                }
            }

            val batchResults = insertStatement.executeBatch()
            batchResults.forEach { result ->
                when (result) {
                    Statement.SUCCESS_NO_INFO -> insertedCount++
                    Statement.EXECUTE_FAILED -> errorCount++
                    else -> if (result > 0) insertedCount++ else ignoredCount++
                }
            }

            connection.commit()
            println(
                """
                ================================================
                Обработка файла ${wordsFile.name} завершена!
                Всего строк: $totalLines
                Успешно добавлено: $insertedCount
                Дубликатов пропущено: $ignoredCount
                Ошибок формата: $errorCount
                ================================================
            """.trimIndent()
            )

        } catch (e: Exception) {
            connection.rollback()
            println(
                """
                ================================================
                ОШИБКА! Транзакция откачена!
                Причина: ${e.message}
                Добавлено до ошибки: $insertedCount
                ================================================
            """.trimIndent()
            )
            throw e
        } finally {
            insertStatement.close()
            connection.autoCommit = true
        }
    }
}

fun initializeDatabase(connection: Connection) {
    val createWordsTable = """
            CREATE TABLE IF NOT EXISTS words (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text VARCHAR (100) NOT NULL UNIQUE,
                translate VARCHAR (100) NOT NULL,
                correctAnswersCount INTEGER DEFAULT 0
            )
        """.trimIndent()
    connection.prepareStatement(createWordsTable).use { statement ->
        statement.executeUpdate()
    }

    connection.prepareStatement("SELECT COUNT(*) FROM words").use { statement ->
        statement.executeQuery().use { resultSet ->

            if (resultSet.next() && resultSet.getInt(1) == 0) {
                println("Таблица words пуста. Загружаю данные из words.txt...")
                val wordsFile = File("words.txt").takeIf { it.exists() }
                    ?: throw FileNotFoundException("Файл words.txt не найден")

                updateDictionary(wordsFile, connection)
                println("Слова из words.txt успешно добавлены в базу")

            }

        }
    }
}










