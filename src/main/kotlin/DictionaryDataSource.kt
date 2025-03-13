import java.io.File
import java.sql.Connection
import java.sql.SQLException

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
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS words (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    text VARCHAR UNIQUE NOT NULL,
                    translate VARCHAR NOT NULL,
                    correctAnswersCount INTEGER DEFAULT 0
                    );
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """ 
                    CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username VARCHAR,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    chat_id INTEGER UNIQUE NOT NULL
                    );
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS user_answers (
                    user_id INTEGER,
                    word_id INTEGER,
                    correct_answer_count INTEGER DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (user_id, word_id),
                    FOREIGN KEY (user_id) REFERENCES users(id),
                    FOREIGN KEY (word_id) REFERENCES words(id)
                    );
                """.trimIndent()
                )
            }
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
                     throw e
                 }
             }
         }
         throw RuntimeException("Не удалось добавить пользователя после нескольких попыток")
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
            connection.createStatement().use { statement ->
                val resultSet = statement.executeQuery("SELECT COUNT(*) FROM words;")
                return resultSet.getInt(1)
            }
        }

        override fun getLearnedWords(): List<Word> {
            return getWords(
                """
             SELECT words.text, words.translate, user_answers.correct_answer_count
                         FROM words
                         JOIN user_answers ON words.id = user_answers.word_id
                         WHERE user_answers.correct_answer_count >= $learningThreshold;
         """.trimIndent()
            )
        }

        override fun getUnlearnedWords(): List<Word> {
            return getWords(
                """
             SELECT words.text, words.translate, COALESCE(user_answers.correct_answer_count, 0) AS correct_answer_count
                                      FROM words
                                      LEFT JOIN user_answers ON words.id = user_answers.word_id
                                      WHERE user_answers.correct_answer_count IS NULL OR user_answers.correct_answer_count < $learningThreshold;
        """.trimIndent()
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

        private fun getWords(query: String): List<Word> {
            val words = mutableListOf<Word>()
            connection.createStatement().use { statement ->
                statement.executeQuery(query).use { resultSet ->
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
            connection.createStatement().use { statement ->
                statement.executeUpdate("DELETE FROM user_answers;")
            }
        }
    }


fun updateDictionary(wordsFile: File, connection: Connection) {
    synchronized(connection) {
        val insertStatement = connection.prepareStatement("INSERT OR IGNORE INTO words (text, translate) VALUES (?, ?)")
        connection.autoCommit = false
        try {
            wordsFile.forEachLine { line ->
                val parts = line.split("|").map { it.trim() }
                if (parts.size != 2) {
                    println("Некорректная строка: $line")
                    return@forEachLine
                }
                insertStatement.apply {
                    setString(1, parts[0])
                    setString(2, parts[1])
                    addBatch()
                }
            }
            insertStatement.executeBatch()
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw RuntimeException("Ошибка при загрузке словаря: ${e.message}", e)
        } finally {
            insertStatement.close()
            connection.autoCommit = true
        }
    }
}






